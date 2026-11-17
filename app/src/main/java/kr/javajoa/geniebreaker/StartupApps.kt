package kr.javajoa.geniebreaker

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

internal object StartupApps {
    fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun allowed(context: Context) = Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(context)
    fun permissionSettings(context: Context): Boolean =
        openScreen(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) ||
            openScreen(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))

    fun select(context: Context, pkg: String, label: String) {
        tvPreferences(context).edit().putString("startup", pkg).putString("startupLabel", label)
            .putInt("lastBoot", boot(context)).apply()
    }
    fun clear(context: Context) {
        tvPreferences(context).edit().remove("startup").remove("startupLabel").remove("lastBoot").remove("wake").apply()
        context.stopService(Intent(context, StartupWakeService::class.java))
    }
    fun due(context: Context, boot: Int, waking: Boolean): Boolean {
        val prefs = tvPreferences(context)
        return prefs.contains("startup") && if (waking) prefs.getBoolean("wake", false)
            else boot >= 0 && boot != prefs.getInt("lastBoot", -1)
    }
    fun launchIfDue(context: Context, boot: Int, waking: Boolean = false): Boolean {
        if (!due(context, boot, waking) || !allowed(context)) return false
        val prefs = tvPreferences(context)
        if (!launchPackage(context, prefs.getString("startup", null).orEmpty())) return false
        prefs.edit().putInt("lastBoot", boot).apply()
        return true
    }

    private var presenting = false
    /** All callers use the main looper. The small status window takes neither focus nor input. */
    fun present(context: Context, waking: Boolean, done: () -> Unit = {}) {
        val app = context.applicationContext
        val boot = boot(app)
        if (presenting || !due(app, boot, waking) || !allowed(app) ||
            app.getSystemService(KeyguardManager::class.java).isDeviceLocked) { done(); return }
        if (Build.VERSION.SDK_INT < 29) { try { launchIfDue(app, boot, waking) } finally { done() }; return }
        val visual = if (Build.VERSION.SDK_INT >= 30) {
            val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            if (display == null) { done(); return }
            app.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else app
        val wm = visual.getSystemService(WindowManager::class.java)
        val prefs = tvPreferences(app)
        val chosen = prefs.getString("startup", null)
        val banner = TextView(visual).apply {
            text = app.getString(R.string.startup_opening, prefs.getString("startupLabel", ""))
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.rgb(26, 44, 65))
            val gap = (12 * resources.displayMetrics.density).toInt()
            setPadding(gap, gap, gap, gap)
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END }
        try { wm.addView(banner, params) } catch (_: RuntimeException) { done(); return }
        presenting = true
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (chosen == prefs.getString("startup", null) && !app.getSystemService(KeyguardManager::class.java).isDeviceLocked)
                    launchIfDue(app, boot, waking)
            } finally {
                presenting = false
                try { wm.removeViewImmediate(banner) } catch (_: IllegalArgumentException) { }
                done()
            }
        }, 850L)
    }
}

class StartupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        StartupWakeService.restore(context)
        val result = goAsync()
        StartupApps.present(context, false) { result?.finish() }
    }
}
