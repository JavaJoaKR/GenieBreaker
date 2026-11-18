package kr.javajoa.geniebreaker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class RemoteButtonService : AccessibilityService() {
    private val presses = ButtonPresses<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val bindings by lazy { RemoteBindings(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        RemoteServiceConnection.connect(this)
        if (BuildConfig.DEBUG) android.util.Log.i("GenieRemoteTest", "Remapping service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
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
        handler.removeCallbacksAndMessages(null)
        presses.clear()
        if (RemoteServiceConnection.disconnect(this) && BuildConfig.DEBUG) {
            android.util.Log.i("GenieRemoteTest", "Remapping service disconnected")
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (RemoteCapture.handle(event)) return true
        // Return the input decision before dispatching a potentially slow platform action.
        return presses.handle(event, bindings::action) { action -> handler.post { runAction(action) } }
    }

    private fun runAction(action: String) {
        val completed = try {
            when (action) {
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "apps" -> openAppsScreen(this)
                "accessibility" -> openAccessibilityScreen(this)
                else -> launchPackage(this, action.removePrefix("app:"))
            }
        } catch (error: RuntimeException) {
            Diagnostics.record(this, "Remote launch", error)
            false
        }
        if (!completed) Toast.makeText(this, "지정한 동작을 실행할 수 없습니다", Toast.LENGTH_SHORT).show()
    }
}
