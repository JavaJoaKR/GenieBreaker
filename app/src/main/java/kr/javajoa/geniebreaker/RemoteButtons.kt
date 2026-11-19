package kr.javajoa.geniebreaker

import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewConfiguration

// Friendly labels and aliases for standard TV inputs.
private val remoteButtons = linkedMapOf(
    KeyEvent.KEYCODE_PROG_RED to "빨간색", KeyEvent.KEYCODE_PROG_GREEN to "초록색",
    KeyEvent.KEYCODE_PROG_YELLOW to "노란색", KeyEvent.KEYCODE_PROG_BLUE to "파란색",
    KeyEvent.KEYCODE_INFO to "정보", KeyEvent.KEYCODE_BOOKMARK to "즐겨찾기",
    KeyEvent.KEYCODE_CAPTIONS to "자막", KeyEvent.KEYCODE_TV to "TV",
    KeyEvent.KEYCODE_TV_TELETEXT to "텔레텍스트",
    KeyEvent.KEYCODE_TV_RADIO_SERVICE to "라디오 서비스", KeyEvent.KEYCODE_TV_DATA_SERVICE to "데이터 방송",
    KeyEvent.KEYCODE_TV_AUDIO_DESCRIPTION to "화면 해설", KeyEvent.KEYCODE_TV_ZOOM_MODE to "화면 비율",
    KeyEvent.KEYCODE_TV_TIMER_PROGRAMMING to "예약 녹화", KeyEvent.KEYCODE_TV_NETWORK to "네트워크 방송",
    KeyEvent.KEYCODE_EXPLORER to "브라우저", KeyEvent.KEYCODE_MUSIC to "음악",
    KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK to "오디오 트랙",
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "재생 / 일시 정지", KeyEvent.KEYCODE_MEDIA_PLAY to "재생",
    KeyEvent.KEYCODE_MEDIA_RECORD to "녹화",
    KeyEvent.KEYCODE_MEDIA_PAUSE to "일시 정지", KeyEvent.KEYCODE_MEDIA_STOP to "정지",
    KeyEvent.KEYCODE_MEDIA_NEXT to "다음 곡",
    KeyEvent.KEYCODE_MEDIA_REWIND to "되감기", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "빨리 감기",
    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD to "뒤로 건너뛰기", KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD to "앞으로 건너뛰기",
    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD to "이전 프레임", KeyEvent.KEYCODE_MEDIA_STEP_FORWARD to "다음 프레임"
)

// Keep the same binding IDs when a known TV button is reported as a Linux scan code.
private val remoteScanButtons = mapOf(
    0x166 to KeyEvent.KEYCODE_INFO,
    0x16c to KeyEvent.KEYCODE_BOOKMARK,
    0x172 to KeyEvent.KEYCODE_CAPTIONS,
    0x18e to KeyEvent.KEYCODE_PROG_RED,
    0x18f to KeyEvent.KEYCODE_PROG_GREEN,
    0x190 to KeyEvent.KEYCODE_PROG_YELLOW,
    0x191 to KeyEvent.KEYCODE_PROG_BLUE
)

// Existing IDs keep their stored meaning. Hold bindings occupy a separate range.
private const val SCAN_BINDING_BASE = 0x10000
private const val HOLD_BINDING_BASE = 0x20000
private const val SCAN_BACKSPACE = 14

private val reservedKeyCodes = setOf(
    KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
    KeyEvent.KEYCODE_11, KeyEvent.KEYCODE_12,
    KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_STB_POWER,
    KeyEvent.KEYCODE_AVR_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_SOFT_SLEEP, KeyEvent.KEYCODE_WAKEUP,
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE,
    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_LAST_CHANNEL,
    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_TV_CONTENTS_MENU,
    KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU, KeyEvent.KEYCODE_MEDIA_TOP_MENU,
    KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_DVR, KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT,
    KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT
) + (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) + (KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9)

private val reservedScanCodes = setOf(
    1, 28, 96, 102, 103, 105, 106, 108,        // Escape, Enter, Home and directions
    113, 114, 115, 116, 139, 141, 142, 143,   // Volume, power, menu and setup
    158, 165, 172, 174, 205, 223, 248,       // Back, previous, home, exit, sleep and mute
    0x160, 0x161, 0x164, 0x165, 0x16a, 0x16d, 0x16e, // OK, Select, Power, Option and guide
    0x18b, 0x192, 0x193, 0x195, 0x198, 0x19c, 0x1b6, // List, channel, previous and menu
    0x220, 0x221, 0x222, 0x223, 0x26b, 0x274,    // D-pad, media menu and unmute
    588,                                        // MA4000 AV receiver power
    55, 71, 72, 73, 75, 76, 77, 79, 80, 81, 82   // Numeric keypad and asterisk
) + (2..11) + (0x200..0x20b)

private fun canBindCode(code: Int): Boolean = when (code) {
    in 1 until SCAN_BINDING_BASE -> code !in reservedKeyCodes
    in (SCAN_BINDING_BASE + 1) until HOLD_BINDING_BASE -> (code - SCAN_BINDING_BASE) !in reservedScanCodes
    else -> false
}

internal fun holdBindingId(code: Int): Int = HOLD_BINDING_BASE + code

internal fun buttonLabel(id: Int): String? {
    val hold = id >= HOLD_BINDING_BASE
    val code = if (hold) id - HOLD_BINDING_BASE else id
    if (!canBindCode(code)) return null
    val label = when {
        code >= SCAN_BINDING_BASE -> "리모컨 버튼 ${code - SCAN_BINDING_BASE}"
        code in KeyEvent.KEYCODE_VIDEO_APP_1..KeyEvent.KEYCODE_VIDEO_APP_8 ->
            "동영상 앱 ${code - KeyEvent.KEYCODE_VIDEO_APP_1 + 1}"
        code in KeyEvent.KEYCODE_FEATURED_APP_1..KeyEvent.KEYCODE_FEATURED_APP_4 ->
            "추천 앱 ${code - KeyEvent.KEYCODE_FEATURED_APP_1 + 1}"
        code in KeyEvent.KEYCODE_DEMO_APP_1..KeyEvent.KEYCODE_DEMO_APP_4 ->
            "앱 바로가기 ${code - KeyEvent.KEYCODE_DEMO_APP_1 + 1}"
        code in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 ->
            "리모컨 버튼 ${code - KeyEvent.KEYCODE_BUTTON_1 + 1}"
        code > KeyEvent.getMaxKeyCode() -> "리모컨 버튼 $code"
        else -> remoteButtons[code] ?: KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }
    return "$label · ${if (hold) "길게 누르기" else "짧게 누르기"}"
}

private fun isRemoteSource(source: Int): Boolean =
    source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD ||
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        source and InputDevice.SOURCE_HDMI == InputDevice.SOURCE_HDMI

internal fun isRemoteInput(event: KeyEvent): Boolean = isRemoteSource(event.source) ||
    event.device?.let { device ->
        !device.isVirtual &&
            (isRemoteSource(device.sources) || device.keyboardType == InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
    } == true

internal fun buttonCode(event: KeyEvent): Int? {
    val androidCode = event.keyCode
    val scan = event.scanCode
    if (androidCode != KeyEvent.KEYCODE_UNKNOWN && !canBindCode(androidCode)) return null
    if (androidCode == KeyEvent.KEYCODE_UNKNOWN &&
        (scan !in 1 until SCAN_BINDING_BASE || scan in reservedScanCodes ||
            (scan == SCAN_BACKSPACE && !event.isFromSource(InputDevice.SOURCE_DPAD)))) return null
    // Physical remotes can be reported as keyboards. Preserve real text entry
    // outside TV/gamepad/HDMI input sources.
    if (!isRemoteInput(event) && (event.isPrintingKey || KeyEvent.isModifierKey(androidCode) ||
            androidCode == KeyEvent.KEYCODE_DEL || androidCode == KeyEvent.KEYCODE_FORWARD_DEL ||
            androidCode == KeyEvent.KEYCODE_TAB ||
            event.device?.let { !it.isVirtual && it.supportsSource(InputDevice.SOURCE_KEYBOARD) } != true)) return null
    return if (androidCode != KeyEvent.KEYCODE_UNKNOWN) androidCode
        else remoteScanButtons[scan] ?: (SCAN_BINDING_BASE + scan)
}

internal data class ButtonPressActions<T>(val click: T?, val hold: T?)

/** Keep the original action and release both halves of an intercepted press together. */
internal class ButtonPresses<T : Any> {
    private data class Key(val device: Int, val code: Int, val scan: Int)
    private data class Press<T>(val downTime: Long, var actions: ButtonPressActions<T>?)
    private val held = mutableMapOf<Key, Press<T>>()

    fun handle(event: KeyEvent, claim: (Int) -> ButtonPressActions<T>?, release: (T) -> Unit): Boolean {
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
                val actions = claim(code) ?: return false
                if (actions.click == null && actions.hold == null) return false
                held[key] = Press(event.downTime, actions)
                true
            }
            KeyEvent.ACTION_UP -> {
                if (!owned) return false
                val actions = held.remove(key)?.actions
                if (!event.isCanceled && actions != null) {
                    val longPress = event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()
                    (if (longPress) actions.hold else actions.click)?.let(release)
                }
                true
            }
            else -> false
        }
    }

    // Suppress actions after interruption while still consuming matching key-ups.
    fun cancelActions() {
        held.values.forEach { it.actions = null }
    }

    fun clear() = held.clear()
}

/** Only the focused registration menu can claim a new press. Its up can arrive later. */
internal object RemoteCapture {
    var claim: ((Int) -> ButtonPressActions<() -> Unit>?)? = null
    var observeUnclaimed: ((KeyEvent) -> Unit)? = null
    private val presses = ButtonPresses<() -> Unit>()

    fun handle(event: KeyEvent): Boolean {
        if (presses.handle(event, { claim?.invoke(it) }, { it() })) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isCanceled) {
            observeUnclaimed?.invoke(event)
        }
        return false
    }
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
