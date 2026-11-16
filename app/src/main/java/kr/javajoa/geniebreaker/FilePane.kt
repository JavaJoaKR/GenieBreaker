package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.*
import android.net.Uri
import android.provider.DocumentsContract as Docs
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.FrameLayout
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Only app-owned files and document trees explicitly granted by the user are exposed. */
internal class FilePane(private val activity: Activity) : FrameLayout(activity) {
    private var engine: FlutterEngine? = null
    private var flutterView: FlutterView? = null
    private var channel: MethodChannel? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences = activity.getSharedPreferences("file_browser", Context.MODE_PRIVATE)
    private val documentPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun show() {
        if (engine != null) return
        val flutter = FlutterEngine(activity); engine = flutter
        channel = MethodChannel(flutter.dartExecutor.binaryMessenger, "genie/ui").apply { setMethodCallHandler { call, result ->
            when (call.method) {
                "pick" -> try {
                    activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 620)
                    result.success(null)
                } catch (_: Exception) { result.error("picker", "이 기기에는 폴더 선택 화면이 없습니다. 앱 내부 파일은 계속 사용할 수 있습니다.", null) }
                "close" -> { visibility = View.GONE; result.success(null) }
                "ready", "list", "clean", "open" -> worker.execute {
                    try {
                        val answer = when (call.method) {
                            "ready" -> listing("")
                            "list" -> listing(call.arguments as String)
                            "clean" -> StorageTools.clean(activity, call.arguments as String)
                            else -> { open(call.arguments as String); null }
                        }
                        activity.runOnUiThread { result.success(answer) }
                    } catch (e: Exception) { activity.runOnUiThread { result.error("files", e.message ?: "파일 요청 실패", null) } }
                }
                else -> result.notImplemented()
            }
        } }
        flutter.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        flutterView = FlutterView(activity).also { addView(it, LayoutParams(-1, -1)); it.attachToFlutterEngine(flutter) }
        flutter.lifecycleChannel.appIsResumed()
    }
    private fun entry(name: String, path: String, directory: Boolean, bytes: Long = 0L) = mapOf(
        "name" to name, "path" to path, "directory" to directory,
        "detail" to if (directory) "폴더" else Formatter.formatFileSize(activity, bytes))
    private fun internal(path: String): File {
        val parts = path.split(':', limit = 2)
        val root = when (parts[0]) { "apks" -> File(activity.filesDir, "edited"); "cache" -> activity.cacheDir; else -> error("허용되지 않은 위치") }.canonicalFile
        val file = File(root, parts.getOrElse(1) { "" }).canonicalFile
        require(file == root || file.path.startsWith(root.path + File.separator)) { "허용된 폴더 밖에는 접근할 수 없습니다" }
        return file
    }
    private fun listing(path: String): Map<String, Any> {
        val entries: List<Map<String, Any>>
        val title: String
        if (path.isEmpty()) {
            title = "파일탐색기"
            entries = listOf(entry("듀얼앱 설치파일", "apks:", true), entry("앱 캐시", "cache:", true)) +
                activity.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { permission ->
                    val uri = Docs.buildDocumentUriUsingTree(permission.uri, Docs.getTreeDocumentId(permission.uri))
                    documentPaths.add(uri.toString()); entry("연결된 폴더 · ${Docs.getTreeDocumentId(permission.uri).substringAfterLast(':')}", uri.toString(), true)
                }
        } else if (path.startsWith("content:")) {
            require(documentPaths.contains(path)) { "폴더를 다시 연결하세요" }
            val uri = Uri.parse(path)
            val children = Docs.buildChildDocumentsUriUsingTree(uri, Docs.getDocumentId(uri))
            title = "연결된 폴더"
            val rows = mutableListOf<Map<String, Any>>()
            activity.contentResolver.query(children, arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME, Docs.Document.COLUMN_MIME_TYPE, Docs.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val child = Docs.buildDocumentUriUsingTree(uri, cursor.getString(0)).toString()
                    documentPaths.add(child)
                    rows += entry(cursor.getString(1) ?: "파일", child, cursor.getString(2) == Docs.Document.MIME_TYPE_DIR, cursor.getLong(3))
                }
            } ?: error("폴더를 열지 못했습니다")
            entries = rows.sortedWith(compareByDescending<Map<String, Any>> { it["directory"] == true }.thenBy { it["name"].toString().lowercase() })
        } else {
            val dir = internal(path); require(!dir.exists() || dir.isDirectory)
            title = if (path == "apks:") "듀얼앱 설치파일" else if (path == "cache:") "앱 캐시" else dir.name
            entries = dir.listFiles().orEmpty().sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }).map {
                val suffix = path.substringAfter(':').trimEnd('/')
                entry(it.name, path.substringBefore(':') + ":" + (if (suffix.isEmpty()) "" else "$suffix/") + it.name, it.isDirectory, it.length())
            }
        }
        return mapOf("files" to true, "title" to title, "entries" to entries,
            "apkSize" to Formatter.formatFileSize(activity, StorageTools.bytes(File(activity.filesDir, "edited"))),
            "cacheSize" to Formatter.formatFileSize(activity, StorageTools.bytes(activity.cacheDir)))
    }
    private fun open(path: String) = FileWork.reading {
        val uri: Uri
        val type: String
        if (path.startsWith("content:")) {
            require(documentPaths.contains(path)); uri = Uri.parse(path)
            type = activity.contentResolver.getType(uri) ?: "application/octet-stream"
        } else {
            val file = internal(path); require(file.isFile) { "파일이 없습니다" }
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
            } else internal(path)
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
            try { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); preferences.edit().putString("last_tree", uri.toString()).apply(); channel?.invokeMethod("refresh", null) }
            catch (e: Exception) { TvMenus.notice(activity, "폴더 접근 권한을 저장하지 못했습니다: ${e.message}") }
        }
        return true
    }
    fun resume() { engine?.lifecycleChannel?.appIsResumed() }
    fun pause() { engine?.lifecycleChannel?.appIsInactive(); engine?.lifecycleChannel?.appIsPaused() }
    fun close() { flutterView?.detachFromFlutterEngine(); engine?.destroy(); worker.shutdown() }
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
