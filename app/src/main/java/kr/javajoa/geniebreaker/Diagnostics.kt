package kr.javajoa.geniebreaker

import android.content.Context
import java.io.File

/** Local identifiers only: no device metadata, identifiers, paths, raw messages or uploads. */
internal object Diagnostics {
    private var installed = false
    @Synchronized fun record(context: Context, operation: String, error: Throwable? = null): String {
        val code = ErrorCodes.create(operation, error?.javaClass?.simpleName)
        try {
            val file = File(context.filesDir, "diagnostics.txt")
            if (file.length() > 64 * 1024) file.writeText(file.readText().takeLast(24 * 1024))
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            // Never persist caller-provided messages: they may contain identifiers or paths.
            val trace = error?.stackTrace?.filter { it.className.startsWith("kr.javajoa.geniebreaker.") }
                ?.take(8)?.joinToString("\n") { "  ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: ""
            file.appendText("$time [$code] ${ErrorCodes.category(operation)} ${ErrorCodes.reason(operation, error?.javaClass?.simpleName)}\n$trace\n")
        } catch (_: Exception) { }
        return code
    }
    @Synchronized fun read(context: Context): String = try {
        File(context.filesDir, "diagnostics.txt").takeIf { it.exists() }?.readText()?.takeLast(20000) ?: "저장된 오류 기록이 없습니다."
    } catch (_: Exception) { "저장된 오류 기록을 열 수 없습니다. [GB-FS-0002]" }
    @Synchronized fun clear(context: Context) { File(context.filesDir, "diagnostics.txt").delete() }
    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        // Old logs may contain raw stack messages; discard them on the first codes-only run.
        val prefs = context.getSharedPreferences("local_diagnostics", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("codes_only", false)) { clear(context); prefs.edit().putBoolean("codes_only", true).apply() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val code = record(context, "Crash", error)
            context.getSharedPreferences("local_diagnostics", Context.MODE_PRIVATE).edit().putString("last_crash", code).commit()
            previous?.uncaughtException(thread, error)
        }
    }
}
