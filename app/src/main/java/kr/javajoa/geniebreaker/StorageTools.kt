package kr.javajoa.geniebreaker

import android.content.Context
import android.os.StatFs
import android.text.format.Formatter
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

/** Cleanup never races an APK read, generation, or update download. */
internal object FileWork {
    val lock = ReentrantReadWriteLock()
    val pending = java.util.concurrent.atomic.AtomicInteger(0)
    fun <T> reading(block: () -> T): T { lock.readLock().lock(); try { return block() } finally { lock.readLock().unlock() } }
}
internal object StorageTools {
    fun bytes(root: File): Long = if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    fun updateRoots(context: Context) = listOf(File(context.cacheDir, "updates"), File(context.filesDir, "updates"), File(context.cacheDir, "update.apk"), File(context.filesDir, "update.apk"))
    fun updateBytes(context: Context) = updateRoots(context).sumOf(::bytes)
    private fun cleanupTargets(context: Context, kind: String): List<File> = when (kind) {
        "apks" -> File(context.filesDir, "edited").listFiles().orEmpty().filter { it.extension in setOf("apk", "json") }
        "cache" -> context.cacheDir.listFiles().orEmpty().filter { it.name != "updates" && it.name != "update.apk" }
        "updates" -> updateRoots(context).filter { it.exists() }
        else -> throw IllegalArgumentException("지원하지 않는 저장 공간 종류")
    }

    fun snapshot(context: Context): Map<String, Any> = FileWork.reading {
        val disk = StatFs(context.filesDir.path)
        fun size(bytes: Long) = Formatter.formatFileSize(context, bytes)
        fun cleanable(kind: String) = size(cleanupTargets(context, kind).sumOf(::bytes))
        mapOf("total" to size(disk.totalBytes), "free" to size(disk.availableBytes),
            "usedFraction" to (1.0 - disk.availableBytes.toDouble() / disk.totalBytes.coerceAtLeast(1)),
            "apkSize" to cleanable("apks"), "cacheSize" to cleanable("cache"), "updateSize" to cleanable("updates"))
    }

    fun pruneUpdates(context: Context) {
        if (!FileWork.lock.writeLock().tryLock()) return
        try {
            if (FileWork.pending.get() != 0) return
            val active = File(context.cacheDir, "updates/update.apk")
            updateRoots(context).forEach { root ->
                if (root.isDirectory) root.listFiles().orEmpty().filter { it != active }.forEach { it.deleteRecursively() }
                else if (root != active) root.delete()
            }
            if (active.exists()) {
                val parsed = context.packageManager.getPackageArchiveInfo(active.path, 0)
                @Suppress("DEPRECATION")
                val code = parsed?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: 0
                if (parsed?.packageName != context.packageName || code <= BuildConfig.VERSION_CODE) active.delete()
            }
        } finally { FileWork.lock.writeLock().unlock() }
    }
    fun clean(context: Context, kind: String): String {
        require(kind in setOf("apks", "cache", "updates"))
        if (!FileWork.lock.writeLock().tryLock()) error("파일 작업 중입니다. 작업이 끝난 뒤 다시 정리하세요.")
        try {
            check(FileWork.pending.get() == 0) { "설치 파일 준비 중. 잠시 후 다시 정리하세요." }
            val failed = cleanupTargets(context, kind).count { !it.deleteRecursively() }
            if (failed > 0) error("일부 파일을 정리하지 못했습니다. 다시 시도하세요.")
            return "선택한 파일을 정리했습니다. 설치된 앱과 서명 키는 유지됩니다."
        } finally { FileWork.lock.writeLock().unlock() }
    }
}
