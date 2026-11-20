package kr.javajoa.geniebreaker

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class RemoteButtonService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val presses = ButtonPresses<String>(handler)
    private val bindings by lazy { RemoteBindings(this) }
    private val prefs by lazy { tvPreferences(this) }
    private val homeShortcut = HomeShortcut(handler,
        enabled = { prefs.getBoolean(HOME_SHORTCUT_ENABLED, true) || isGProjectHome(this) },
        open = { openGenieBreaker() },
        reset = {
            presses.cancelActions()
            bindings.clear()
        }
    )
    private var reportedInputFailure = false

    override fun onCreate() {
        super.onCreate()
        // Android may bind the service before the activity is opened after an update.
        Diagnostics.install(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        reportedInputFailure = false
        RemoteServiceConnection.connect(this)
        if (BuildConfig.DEBUG) android.util.Log.i("GenieRemoteTest", "Remapping service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        homeShortcut.cancel()
        handler.removeCallbacksAndMessages(null)
        presses.cancelActions()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        homeShortcut.cancel()
        handler.removeCallbacksAndMessages(null)
        presses.clear()
        if (RemoteServiceConnection.disconnect(this) && BuildConfig.DEBUG) {
            android.util.Log.i("GenieRemoteTest", "Remapping service disconnected")
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return try {
            homeShortcut.observe(event)
            if (RemoteCapture.handle(event)) return true
            // Return the input decision before dispatching a potentially slow platform action.
            presses.handle(event, bindings::actions) { action -> handler.post { runAction(action) } }
        } catch (error: RuntimeException) {
            homeShortcut.cancel()
            presses.cancelActions()
            if (!reportedInputFailure) {
                reportedInputFailure = true
                handler.post { Diagnostics.record(this, "Remote key input", error) }
            }
            false
        }
    }

    private fun openGenieBreaker(): Boolean =
        openScreen(this, Intent.makeMainActivity(ComponentName(this, MainActivity::class.java)))

    private fun runAction(action: String) {
        val completed = try {
            when (action) {
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "apps" -> openAppsScreen(this)
                "accessibility" -> openAccessibilityScreen(this)
                else -> {
                    val target = action.removePrefix("app:")
                    if (target == packageName) openGenieBreaker() else launchPackage(this, target)
                }
            }
        } catch (error: RuntimeException) {
            Diagnostics.record(this, "Remote launch", error)
            false
        }
        if (!completed) {
            try {
                Toast.makeText(this, "매핑한 동작을 실행할 수 없습니다", Toast.LENGTH_SHORT).show()
            } catch (error: RuntimeException) {
                Diagnostics.record(this, "Remote action feedback", error)
            }
        }
    }
}
