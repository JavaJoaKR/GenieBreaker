package kr.javajoa.geniebreaker

import android.content.Context
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
            check(FileWork.pending.get() == 0) { "설치 파일을 준비하고 있습니다. 잠시 후 다시 정리하세요." }
            val root = if (kind == "apks") File(context.filesDir, "edited") else context.cacheDir
            val files = if (kind == "updates") updateRoots(context).filter { it.exists() }
                else root.listFiles().orEmpty().filter { (kind == "cache" && it.name !in setOf("updates", "update.apk")) || (kind == "apks" && it.extension in listOf("apk", "json")) }.toList()
            val failed = files.count { !it.deleteRecursively() }
            if (failed > 0) error("일부 파일을 정리하지 못했습니다. 다시 시도하세요.")
            return "정리했습니다. 설치된 앱과 서명 키는 유지됩니다."
        } finally { FileWork.lock.writeLock().unlock() }
    }
}
