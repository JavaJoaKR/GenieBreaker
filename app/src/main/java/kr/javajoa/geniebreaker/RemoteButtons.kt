package kr.javajoa.geniebreaker

import android.view.InputDevice
import android.view.KeyEvent

/** Leave navigation, system power, volume and text input with Android. */
private val remoteButtons = linkedMapOf(
    KeyEvent.KEYCODE_PROG_RED to "빨간색", KeyEvent.KEYCODE_PROG_GREEN to "초록색",
    KeyEvent.KEYCODE_PROG_YELLOW to "노란색", KeyEvent.KEYCODE_PROG_BLUE to "파란색",
    KeyEvent.KEYCODE_CHANNEL_UP to "채널 +", KeyEvent.KEYCODE_CHANNEL_DOWN to "채널 −",
    KeyEvent.KEYCODE_GUIDE to "편성표", KeyEvent.KEYCODE_INFO to "정보",
    KeyEvent.KEYCODE_BOOKMARK to "즐겨찾기", KeyEvent.KEYCODE_CAPTIONS to "자막",
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "재생 / 일시 정지", KeyEvent.KEYCODE_MEDIA_PLAY to "재생",
    KeyEvent.KEYCODE_MEDIA_PLAY to "재생", KeyEvent.KEYCODE_MEDIA_RECORD to "녹화",
    KeyEvent.KEYCODE_MEDIA_PAUSE to "일시 정지", KeyEvent.KEYCODE_MEDIA_STOP to "정지",
    KeyEvent.KEYCODE_MEDIA_PREVIOUS to "이전 곡", KeyEvent.KEYCODE_MEDIA_NEXT to "다음 곡",
    KeyEvent.KEYCODE_MEDIA_REWIND to "되감기", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "빨리 감기",
    KeyEvent.KEYCODE_VIDEO_APP_1 to "동영상 앱 1", KeyEvent.KEYCODE_VIDEO_APP_2 to "동영상 앱 2",
    KeyEvent.KEYCODE_VIDEO_APP_3 to "동영상 앱 3", KeyEvent.KEYCODE_VIDEO_APP_4 to "동영상 앱 4"
)

// Recover only known non-navigation Linux input codes when firmware leaves them unmapped.
private val remoteScanButtons = mapOf(
    0x18e to KeyEvent.KEYCODE_PROG_RED,
    0x18f to KeyEvent.KEYCODE_PROG_GREEN,
    0x190 to KeyEvent.KEYCODE_PROG_YELLOW,
    0x191 to KeyEvent.KEYCODE_PROG_BLUE,
    0x192 to KeyEvent.KEYCODE_CHANNEL_UP,
    0x193 to KeyEvent.KEYCODE_CHANNEL_DOWN
)

internal fun buttonLabel(code: Int): String? = when {
    code in (KeyEvent.getMaxKeyCode() + 1)..65535 -> "리모컨 버튼 $code"
    else -> remoteButtons[code]
}

private fun buttonCode(event: KeyEvent): Int? {
    if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN) return event.keyCode.takeIf { buttonLabel(it) != null }
    val remoteSource = event.isFromSource(InputDevice.SOURCE_DPAD) ||
        event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
        event.device?.keyboardType == InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC
    return if (remoteSource) remoteScanButtons[event.scanCode] else null
}

/** Claim down, repeat and up together, with the action that was bound at the first down. */
internal class ButtonPresses<T : Any> {
    private data class Key(val device: Int, val code: Int, val scan: Int)
    private data class Press<T>(val downTime: Long, var value: T?)
    private val held = mutableMapOf<Key, Press<T>>()

    fun handle(event: KeyEvent, claim: (Int) -> T?, release: (T) -> Unit): Boolean {
        val code = buttonCode(event) ?: return false
        val key = Key(event.deviceId, code, event.scanCode)
        val current = held[key]
        val owned = current != null && current.downTime == event.downTime
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (owned) return true
                if (event.repeatCount > 0) return false
                held.remove(key)
                if (event.isCanceled || !event.hasNoModifiers()) return false
                val action = claim(code) ?: return false
                held[key] = Press(event.downTime, action)
                true
            }
            KeyEvent.ACTION_UP -> {
                if (!owned) return false
                held.remove(key)?.value?.let { if (!event.isCanceled) release(it) }
                true
            }
            else -> false
        }
    }

    // Suppress actions after interruption while still consuming matching key-ups.
    fun cancelActions() {
        held.values.forEach { it.value = null }
    }

    fun clear() = held.clear()
}

/** Only the focused registration menu can claim a new press. Its up can arrive later. */
internal object RemoteCapture {
    var choose: ((Int) -> Boolean)? = null
    private val presses = ButtonPresses<Unit>()

    fun handle(event: KeyEvent): Boolean =
        presses.handle(event, { code -> if (choose?.invoke(code) == true) Unit else null }, {})
}

internal fun remoteNavigationCommand(event: KeyEvent): String? {
    if (!event.hasNoModifiers()) return null
    // Preserve editing shortcuts for an alphabetic keyboard. TV remotes
    // with dedicated D-pad sources and controller buttons use the bridge.
    val typingKeyboard = event.device?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
        !event.isFromSource(InputDevice.SOURCE_DPAD)
    return when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> if (typingKeyboard) null else "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> if (typingKeyboard) null else "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> if (typingKeyboard) null else "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> if (typingKeyboard) null else "right"
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT -> "activate"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (typingKeyboard) null else "activate"
        // Linux input-event-codes.h. Only recover these when this device's
        // key layout did not map them to an Android key code.
        KeyEvent.KEYCODE_UNKNOWN -> when (event.scanCode) {
            103 -> "up"
            108 -> "down"
            105 -> "left"
            106 -> "right"
            0x160, 0x161 -> "activate" // KEY_OK, KEY_SELECT
            else -> null
        }
        else -> null
    }
}
