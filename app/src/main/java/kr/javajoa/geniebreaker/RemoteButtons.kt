package kr.javajoa.geniebreaker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/** Leave navigation, system power, volume and text input with Android. */
internal val remoteButtons = linkedMapOf(
    KeyEvent.KEYCODE_PROG_RED to "빨간색", KeyEvent.KEYCODE_PROG_GREEN to "초록색",
    KeyEvent.KEYCODE_PROG_YELLOW to "노란색", KeyEvent.KEYCODE_PROG_BLUE to "파란색",
    KeyEvent.KEYCODE_CHANNEL_UP to "채널 +", KeyEvent.KEYCODE_CHANNEL_DOWN to "채널 −",
    KeyEvent.KEYCODE_GUIDE to "편성표", KeyEvent.KEYCODE_INFO to "정보",
    KeyEvent.KEYCODE_BOOKMARK to "즐겨찾기", KeyEvent.KEYCODE_CAPTIONS to "자막",
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "재생 / 일시 정지", KeyEvent.KEYCODE_MEDIA_PLAY to "재생",
    KeyEvent.KEYCODE_MEDIA_PAUSE to "일시 정지", KeyEvent.KEYCODE_MEDIA_STOP to "정지",
    KeyEvent.KEYCODE_MEDIA_PREVIOUS to "이전 곡", KeyEvent.KEYCODE_MEDIA_NEXT to "다음 곡",
    KeyEvent.KEYCODE_MEDIA_REWIND to "되감기", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "빨리 감기",
    KeyEvent.KEYCODE_VIDEO_APP_1 to "동영상 앱 1", KeyEvent.KEYCODE_VIDEO_APP_2 to "동영상 앱 2",
    KeyEvent.KEYCODE_VIDEO_APP_3 to "동영상 앱 3", KeyEvent.KEYCODE_VIDEO_APP_4 to "동영상 앱 4"
)

// Some TV remotes deliver their own key codes outside the Android range.
internal fun buttonLabel(code: Int): String? = remoteButtons[code] ?: if (code in (KeyEvent.getMaxKeyCode() + 1)..65535) "리모컨 버튼 $code" else null

internal fun tvPreferences(context: Context) = context.getSharedPreferences("tv_controls", Context.MODE_PRIVATE)

internal val buttonActions = linkedMapOf("home" to "홈 화면", "back" to "뒤로", "apps" to "앱 목록", "accessibility" to "접근성 설정")

internal fun openScreen(context: Context, intent: Intent?): Boolean {
    if (intent == null) return false
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: android.content.ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }
}

internal fun launchIntent(context: Context, pkg: String): Intent? {
    if (pkg == context.packageName || pkg.isBlank()) return null
    val pm = context.packageManager
    return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
}

internal fun launchPackage(context: Context, pkg: String): Boolean = openScreen(context, launchIntent(context, pkg))

internal fun openAppsScreen(context: Context) =
    openScreen(context, Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) ||
        openScreen(context, Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))

/** Each consumed down owns its matching up, even if the binding changes in between. */
internal class ButtonPresses {
    private val held = mutableMapOf<Pair<Int, Int>, String>()
    fun handle(event: KeyEvent, binding: (Int) -> String?, run: (String) -> Unit): Boolean {
        val code = event.keyCode
        if (buttonLabel(code) == null) return false
        val key = event.deviceId to code
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    held.remove(key)
                    val action = binding(code) ?: return false
                    held[key] = action
                }
                key in held
            }
            KeyEvent.ACTION_UP -> {
                val action = held.remove(key) ?: return false
                if (!event.isCanceled) run(action)
                true
            }
            else -> false
        }
    }
    fun clear() = held.clear()
}

class RemoteButtonService : AccessibilityService() {
    private val presses = ButtonPresses()
    override fun onServiceConnected() {
        super.onServiceConnected()
        if (BuildConfig.DEBUG) android.util.Log.i("GenieRemoteTest", "Remapping service connected")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = presses.clear()
    override fun onDestroy() { presses.clear(); super.onDestroy() }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // A user explicitly opened the foreground button chooser. Prefer capture over its old binding.
        if (RemoteCapture.handle(event)) return true
        return presses.handle(event, { code -> tvPreferences(this).getString("button.$code", null) }) { action ->
            val ok = when (action) {
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "apps" -> openAppsScreen(this)
                "accessibility" -> openScreen(this, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                else -> action.startsWith("app:") && launchPackage(this, action.removePrefix("app:"))
            }
            if (!ok) Toast.makeText(this, "지정한 동작을 실행할 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
}

/** The same event may arrive at the service or at the focused Activity. Only listen inside the chooser. */
internal object RemoteCapture {
    var choose: ((Int) -> Boolean)? = null
    private var held: Pair<Int, Int>? = null
    fun handle(event: KeyEvent): Boolean {
        if (buttonLabel(event.keyCode) == null) return false
        val key = event.deviceId to event.keyCode
        if (key == held && (event.action == KeyEvent.ACTION_UP || event.repeatCount > 0)) {
            if (event.action == KeyEvent.ACTION_UP) held = null
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0 || choose?.invoke(event.keyCode) != true) return false
        held = key
        return true
    }
}

