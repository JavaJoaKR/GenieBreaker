package kr.javajoa.geniebreaker

import android.content.Context

internal class AppLock(context: Context) {
    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    private var unlocked = false
    val enabled get() = prefs.getBoolean("enabled", false) || prefs.contains("pin")
    val locked get() = enabled && !unlocked

    fun relock(): Boolean {
        val changed = enabled && unlocked
        unlocked = false
        return changed
    }

    fun enable(): Boolean {
        if (!prefs.edit().putBoolean("enabled", true).remove("pin").commit()) return false
        unlocked = true
        return true
    }

    fun disable(): Boolean = prefs.edit().remove("enabled").remove("pin").commit()

    fun unlock(code: String): Boolean {
        if (!locked || !code.equals(BuildConfig.BUILD_COMMIT, ignoreCase = true)) return false
        if (prefs.contains("pin")) {
            prefs.edit().putBoolean("enabled", true).remove("pin").apply()
        }
        unlocked = true
        return true
    }
}
