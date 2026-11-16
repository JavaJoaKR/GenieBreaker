package kr.javajoa.geniebreaker

import android.content.Context
import java.io.File

internal object Diagnostics {
    private var installed = false
    @Synchronized fun record(context: Context, operation: String, error: Throwable? = null) {
        try {
            val file = File(context.filesDir, "diagnostics.txt")
            if (file.length() > 96 * 1024) file.writeText(file.readText().takeLast(32 * 1024))
            file.appendText("\n${System.currentTimeMillis()} $operation\n${error?.stackTraceToString() ?: ""}\n")
        } catch (_: Exception) { }
    }
    fun read(context: Context): String = try { File(context.filesDir, "diagnostics.txt").takeIf { it.exists() }?.readText()?.takeLast(20000) ?: "기록된 오류가 없습니다." } catch (_: Exception) { "기록을 읽을 수 없습니다." }
    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(context, "Uncaught exception: ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
