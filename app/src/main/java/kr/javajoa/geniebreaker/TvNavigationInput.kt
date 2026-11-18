package kr.javajoa.geniebreaker

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/** Normalize Android TV input before it reaches the Flutter keyboard engine. */
internal class TvNavigationInput(private val send: (String) -> Unit) {
    private data class Key(val device: Int, val code: Int, val scan: Int)

    // A press stays on its original path even if it opens or closes a text field.
    private val held = mutableMapOf<Key, Boolean>()
    private val hats = mutableMapOf<Int, String>()

    fun key(event: KeyEvent, editingText: Boolean): Boolean {
        val key = Key(event.deviceId, event.keyCode, if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) event.scanCode else 0)
        if (event.action == KeyEvent.ACTION_UP) held.remove(key)?.let { return it }
        val command = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT -> "activate"
            KeyEvent.KEYCODE_UNKNOWN -> when (event.scanCode) {
                103 -> "up"
                108 -> "down"
                105 -> "left"
                106 -> "right"
                0x160, 0x161 -> "activate" // Linux KEY_OK and KEY_SELECT.
                else -> return false
            }
            else -> return false
        }
        val dedicatedConfirm = event.keyCode in setOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT
        )
        val useEditor = editingText && !dedicatedConfirm
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val consumed = if (event.repeatCount == 0) {
                    (!useEditor).also { held[key] = it }
                } else held.getOrPut(key) { !useEditor }
                if (consumed && !useEditor && !event.isCanceled &&
                    (command != "activate" || event.repeatCount == 0)) send(command)
                consumed
            }
            KeyEvent.ACTION_UP -> {
                if (useEditor) return false
                // Some remotes deliver only a release at the app window.
                if (!event.isCanceled) send(command)
                true
            }
            else -> false
        }
    }

    fun motion(event: MotionEvent, editingText: Boolean): Boolean {
        if (editingText || event.actionMasked != MotionEvent.ACTION_MOVE ||
            !(event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_DPAD))) {
            return false
        }
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val device = event.deviceId
        val command = when {
            abs(x) < .5f && abs(y) < .5f -> return hats.remove(device) != null
            abs(y) >= abs(x) -> if (y < 0) "up" else "down"
            else -> if (x < 0) "left" else "right"
        }
        if (hats.put(device, command) != command) send(command)
        return true
    }

    fun pause() {
        held.clear()
        hats.clear()
    }
}
