package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract as Docs
import android.provider.Settings
import android.text.format.Formatter
import androidx.documentfile.provider.DocumentFile
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Local explorer. Only app folders and explicitly granted document trees are reachable. */
internal class FileController(private val activity: Activity, private val changed: () -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val known = mutableMapOf<String, DocumentFile>()
    private val roots = mutableSetOf<String>()
    private val writable = mutableSetOf<String>()
    private var clipboard: String? = null
    private var cut = false
    private val resolver get() = activity.contentResolver
    // Do not offer cloud-backed document trees: this explorer must never upload local data.
    private fun isLocalTree(uri: Uri) = uri.scheme == "content" && uri.authority in setOf(
        "com.android.externalstorage.documents", "com.android.providers.downloads.documents", "com.android.providers.media.documents")
    fun handle(call: MethodCall, result: MethodChannel.Result): Boolean {
        if (call.method == "pick") {
            activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(Intent.EXTRA_LOCAL_ONLY, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 620)
            result.success(null); return true
        }
        if (call.method !in setOf("files.list", "files.action", "open", "storage.list", "clean")) return false
        worker.execute {
            try {
                val answer: Any? = when (call.method) {
                    "files.list" -> FileWork.reading { listing(call.arguments as? String ?: "") }
                    "storage.list" -> storage()
                    "clean" -> StorageTools.clean(activity, call.arguments as String)
                    "open" -> { open(call.arguments as String); null }
                    else -> mutate(call.arguments as Map<*, *>)
                }
                activity.runOnUiThread { if (!activity.isDestroyed) result.success(answer) }
            } catch (error: Exception) {
                val code = Diagnostics.record(activity, "File request", error)
                activity.runOnUiThread { if (!activity.isDestroyed) result.error("files", "${error.message ?: "파일 요청 실패"} [$code]", null) }
            }
        }
        return true
    }
    private fun rootFile(kind: String): File = when (kind) {
        "local" -> File(activity.filesDir, "browser").apply { mkdirs() }
        "apks" -> File(activity.filesDir, "edited").apply { mkdirs() }
        "cache" -> activity.cacheDir
        else -> error("허용되지 않은 위치")
    }
    internal fun local(path: String): File {
        val parts = path.split(':', limit = 2)
        val root = rootFile(parts[0]).canonicalFile
        val file = File(root, parts.getOrElse(1) { "" }).canonicalFile
        require(file == root || file.path.startsWith(root.path + File.separator)) { "허용된 폴더 밖에는 접근할 수 없습니다" }
        return file
    }
    private fun key(doc: DocumentFile): String = if (doc.uri.scheme == "file") "file:${File(requireNotNull(doc.uri.path)).canonicalPath}" else "${doc.uri.authority}:${Docs.getDocumentId(doc.uri)}"
    private fun document(path: String): DocumentFile {
        val doc = if (path.startsWith("content:")) known[path] ?: error("폴더를 다시 연결하세요") else DocumentFile.fromFile(local(path))
        require(doc.exists()) { "파일이 없습니다. 새로고침하세요." }
        return doc
    }
    private fun remember(doc: DocumentFile, parentPath: String, write: Boolean): String {
        val path = if (doc.uri.scheme == "file") {
            val root = rootFile(parentPath.substringBefore(':')).canonicalFile
            val relative = File(requireNotNull(doc.uri.path)).canonicalFile.relativeTo(root).invariantSeparatorsPath
            parentPath.substringBefore(':') + ":" + relative
        } else doc.uri.toString()
        known[path] = doc
        if (write) writable.add(path)
        return path
    }
    private fun entry(doc: DocumentFile, path: String): Map<String, Any> = mapOf(
        "name" to (doc.name ?: "폴더"), "path" to path, "directory" to doc.isDirectory,
        "bytes" to doc.length(), "modified" to doc.lastModified(), "writable" to (path in writable && doc.canWrite()),
        "root" to (path in roots), "detail" to if (doc.isDirectory) "폴더" else Formatter.formatFileSize(activity, doc.length()))
    internal fun listing(path: String): Map<String, Any> {
        val entries: List<Map<String, Any>>
        val title: String
        if (path.isEmpty()) {
            roots.clear()
            val items = mutableListOf<Map<String, Any>>()
            for ((kind, label) in listOf("local" to "내 파일", "apks" to "듀얼앱 설치파일", "cache" to "앱 캐시")) {
                val handle = "$kind:"
                roots.add(handle)
                val doc = DocumentFile.fromFile(rootFile(kind)); known[handle] = doc
                if (kind == "local") writable.add(handle)
                items += entry(doc, handle) + mapOf("name" to label)
            }
            resolver.persistedUriPermissions.filter { it.isReadPermission && isLocalTree(it.uri) }.forEach { permission ->
                DocumentFile.fromTreeUri(activity, permission.uri)?.let { doc ->
                    val handle = doc.uri.toString(); known[handle] = doc; roots.add(handle)
                    if (permission.isWritePermission) writable.add(handle) else writable.remove(handle)
                    items += entry(doc, handle)
                }
            }
            entries = items; title = "파일탐색기"
        } else {
            val doc = document(path); require(doc.isDirectory) { "폴더가 아닙니다" }
            title = doc.name ?: "폴더"
            entries = doc.listFiles().map { child ->
                val childPath = remember(child, path, path in writable)
                // Canonical validation also rejects symlinks outside an app root.
                if (!childPath.startsWith("content:")) local(childPath)
                entry(child, childPath)
            }.sortedWith(compareByDescending<Map<String, Any>> { it["directory"] == true }.thenBy { it["name"].toString().lowercase() })
        }
        return mapOf("title" to title, "path" to path, "entries" to entries,
            "writable" to (path in writable), "clipboard" to (clipboard?.let { known[it]?.name } ?: ""), "cut" to cut)
    }
    private fun validName(name: String): String {
        require(name.isNotBlank() && name !in setOf(".", "..") && name.length <= 120 && name.none { it == '/' || it == '\\' || it.code < 32 }) { "올바른 이름을 입력하세요" }
        return name
    }
    private fun requireWrite(path: String, allowRoot: Boolean = false): DocumentFile {
        require(path in writable && (allowRoot || path !in roots)) { "이 위치는 읽기 전용입니다. 쓰기 권한으로 폴더를 연결하세요." }
        return document(path).also { require(it.canWrite()) { "쓰기 권한이 없습니다" } }
    }
    private data class Snapshot(val doc: DocumentFile, val children: List<Snapshot>)
    private fun capture(doc: DocumentFile, visited: MutableSet<String>, depth: Int = 0): Snapshot {
        require(depth <= 64 && visited.size < 10000 && visited.add(key(doc))) { "폴더가 너무 크거나 순환 참조가 있습니다" }
        // Canonical descendants of local folders must stay below the selected source.
        val children = if (doc.isDirectory) doc.listFiles().map { child ->
            if (child.uri.scheme == "file") require(File(requireNotNull(child.uri.path)).canonicalPath.startsWith(File(requireNotNull(doc.uri.path)).canonicalPath + File.separator)) { "심볼릭 링크는 복사할 수 없습니다" }
            capture(child, visited, depth + 1)
        } else emptyList()
        return Snapshot(doc, children)
    }
    private fun copy(snapshot: Snapshot, target: DocumentFile): DocumentFile {
        val source = snapshot.doc
        val name = validName(source.name ?: "파일")
        require(target.findFile(name) == null) { "같은 이름이 있습니다. 이름을 바꾸거나 다른 폴더를 선택하세요." }
        val created = if (source.isDirectory) target.createDirectory(name) else target.createFile(source.type ?: "application/octet-stream", name)
        requireNotNull(created) { "파일을 만들지 못했습니다" }
        try {
            if (source.isDirectory) snapshot.children.forEach { copy(it, created) }
            else resolver.openInputStream(source.uri)?.use { input ->
                resolver.openOutputStream(created.uri, "wt")?.use { output -> input.copyTo(output) } ?: error("파일을 쓸 수 없습니다")
            } ?: error("파일을 읽지 못했습니다")
            return created
        } catch (error: Exception) {
            val removed = created.delete()
            if (!removed) throw java.io.IOException("복사 실패. 대상에 불완전한 파일이 남았습니다. 직접 확인하세요.", error)
            throw error
        }
    }
    internal fun mutate(args: Map<*, *>): Map<String, Any> {
        check(FileWork.lock.writeLock().tryLock()) { "파일 작업 중입니다. 잠시 후 다시 시도하세요." }
        try {
            check(FileWork.pending.get() == 0) { "설치 파일을 준비하고 있습니다. 잠시 후 다시 시도하세요." }
            val action = args["action"] as String
            val path = args["path"] as? String ?: ""
            var selected = ""
            when (action) {
                "copy", "cut" -> {
                    require(path !in roots) { "루트 폴더는 선택할 수 없습니다" }
                    if (action == "cut") requireWrite(path) else document(path)
                    clipboard = path; cut = action == "cut"
                }
                "mkdir" -> {
                    val parent = requireWrite(path, true); val name = validName(args["name"] as String)
                    require(parent.findFile(name) == null) { "같은 이름이 있습니다" }
                    selected = remember(requireNotNull(parent.createDirectory(name)) { "폴더를 만들지 못했습니다" }, path, true)
                }
                "rename" -> {
                    val doc = requireWrite(path); val name = validName(args["name"] as String)
                    val parent = args["parent"] as String
                    require(document(parent).findFile(name) == null) { "같은 이름이 있습니다" }
                    require(doc.renameTo(name)) { "이름을 바꾸지 못했습니다" }
                    known.remove(path); writable.remove(path)
                    selected = remember(doc, parent, true)
                    if (clipboard == path) clipboard = selected
                }
                "delete" -> {
                    require(requireWrite(path).delete()) { "일부 파일을 삭제하지 못했습니다. 새로고침 후 확인하세요." }
                    known.remove(path); writable.remove(path); if (clipboard == path) clipboard = null
                }
                "paste" -> {
                    val sourcePath = clipboard ?: error("복사하거나 이동할 파일을 선택하세요")
                    val source = if (cut) requireWrite(sourcePath) else document(sourcePath)
                    val target = requireWrite(path, true); require(target.isDirectory)
                    val visited = mutableSetOf<String>(); val snapshot = capture(source, visited)
                    require(key(target) !in visited) { "자기 자신이나 하위 폴더로 복사·이동할 수 없습니다" }
                    val created = copy(snapshot, target)
                    selected = remember(created, path, true)
                    if (cut) {
                        require(source.delete()) { "복사는 완료됐지만 원본을 삭제하지 못했습니다. 양쪽 파일을 확인하세요." }
                        known.remove(sourcePath); writable.remove(sourcePath); clipboard = null
                    }
                }
                else -> error("지원하지 않는 작업")
            }
            return mapOf("selected" to selected)
        } finally { FileWork.lock.writeLock().unlock() }
    }
    private fun storage(): Map<String, Any> = FileWork.reading {
        val stat = StatFs(activity.filesDir.path)
        mapOf("total" to Formatter.formatFileSize(activity, stat.totalBytes), "free" to Formatter.formatFileSize(activity, stat.availableBytes),
            "usedFraction" to (1.0 - stat.availableBytes.toDouble() / stat.totalBytes.coerceAtLeast(1)),
            "apkSize" to Formatter.formatFileSize(activity, StorageTools.bytes(File(activity.filesDir, "edited"))),
            "cacheSize" to Formatter.formatFileSize(activity, (StorageTools.bytes(activity.cacheDir) - StorageTools.bytes(File(activity.cacheDir, "updates")) - File(activity.cacheDir, "update.apk").length()).coerceAtLeast(0)),
            "updateSize" to Formatter.formatFileSize(activity, StorageTools.updateBytes(activity)))
    }
    private fun open(path: String) = FileWork.reading {
        val uri: Uri
        val type: String
        if (path.startsWith("content:")) {
            require(known.containsKey(path)); uri = Uri.parse(path)
            type = activity.contentResolver.getType(uri) ?: "application/octet-stream"
        } else {
            val file = local(path); require(file.isFile) { "파일이 없습니다" }
            uri = BrowseFiles.grant(activity, file)
            type = BrowseFiles.type(file)
        }
        if (type == "application/vnd.android.package-archive" || path.endsWith(".apk", true)) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                activity.runOnUiThread { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                error("설치 권한을 허용한 뒤 파일을 다시 선택하세요")
            }
            val file = if (path.startsWith("content:")) File(activity.cacheDir, "imports/${UUID.randomUUID()}.apk").apply {
                parentFile?.mkdirs()
                try { activity.contentResolver.openInputStream(uri)?.use { input -> outputStream().use { out ->
                    val buffer = ByteArray(65536); var total = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 200L * 1024 * 1024) { "200MB 이하 APK만 설치할 수 있습니다" }; out.write(buffer, 0, count) }
                } } ?: error("파일을 읽지 못했습니다") } catch (e: Exception) { delete(); throw e }
            } else local(path)
            val pkg = activity.packageManager.getPackageArchiveInfo(file.path, 0) ?: error("유효한 APK가 아닙니다")
            SessionInstaller.submit(activity, file, pkg.packageName)
        } else activity.runOnUiThread {
            try { activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("파일", uri) }) }
            catch (_: Exception) { TvMenus.notice(activity, "이 파일을 열 수 있는 앱이 없습니다") }
        }
    }
    fun result(request: Int, result: Int, data: Intent?): Boolean {
        if (request != 620) return false
        if (result == Activity.RESULT_OK) data?.data?.let { uri ->
            try {
                require(isLocalTree(uri)) { "내부 저장소나 USB의 로컬 폴더만 연결할 수 있습니다" }
                val flags = requireNotNull(data).flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "폴더 읽기 권한이 필요합니다" }
                if (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } else resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                changed()
            } catch (error: Exception) { TvMenus.notice(activity, "폴더 권한을 저장하지 못했습니다. [${Diagnostics.record(activity, "Folder permission", error)}]") }
        }
        return true
    }
    fun close() { worker.shutdown() }
}

internal object BrowseFiles {
    private data class Grant(val file: File, val expires: Long)
    private val grants = java.util.concurrent.ConcurrentHashMap<String, Grant>()
    fun grant(context: Context, file: File): Uri {
        val now = System.currentTimeMillis(); grants.entries.removeIf { it.value.expires < now }
        val id = UUID.randomUUID().toString(); grants[id] = Grant(file.canonicalFile, now + 3600000)
        return Uri.parse("content://${context.packageName}.updates/browse/$id")
    }
    fun file(uri: Uri): File = grants[uri.lastPathSegment]?.takeIf { it.expires > System.currentTimeMillis() }?.file ?: throw java.io.FileNotFoundException()
    fun type(file: File): String = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
}
