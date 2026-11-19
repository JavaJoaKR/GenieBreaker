package kr.javajoa.geniebreaker

import android.os.Handler
import android.view.KeyEvent

internal const val HOME_SHORTCUT_ENABLED = "homeShortcutEnabled"

internal class HomeShortcut(
    private val handler: Handler,
    private val enabled: () -> Boolean,
    private val open: () -> Unit,
    private val reset: () -> Unit
) {
    private class Press(val downTime: Long) {
        var opened = false
        var reset = false
    }

    private val held = mutableMapOf<Int, Press>()

    fun observe(event: KeyEvent) {
        if (event.keyCode != KeyEvent.KEYCODE_HOME &&
            (event.keyCode != KeyEvent.KEYCODE_UNKNOWN || (event.scanCode != 102 && event.scanCode != 172))) return

        val device = event.deviceId
        if (!enabled() || event.isCanceled) {
            finish(device)
            return
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (held[device]?.downTime == event.downTime) return
            finish(device)
            if (!event.hasNoModifiers()) return
            val press = Press(event.downTime)
            held[device] = press
            handler.postAtTime({
                if (held[device] === press && enabled()) openOnce(press)
            }, press, event.downTime + OPEN_AFTER)
            return
        }

        val press = held[device]?.takeIf { it.downTime == event.downTime } ?: return
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return
        val elapsed = event.eventTime - press.downTime
        if (elapsed >= OPEN_AFTER) openOnce(press)
        // A repeat event or a matching key-up proves the press lasted this long.
        // A missing key-up alone must never cause a reset.
        if (elapsed >= RESET_AFTER && !press.reset) {
            press.reset = true
            reset()
        }
        if (event.action == KeyEvent.ACTION_UP) finish(device)
    }

    private fun openOnce(press: Press) {
        if (press.opened) return
        press.opened = true
        handler.removeCallbacksAndMessages(press)
        open()
    }

    private fun finish(device: Int) {
        held.remove(device)?.let(handler::removeCallbacksAndMessages)
    }

    fun cancel() {
        held.values.forEach(handler::removeCallbacksAndMessages)
        held.clear()
    }

    private companion object {
        const val OPEN_AFTER = 5_000L
        const val RESET_AFTER = 15_000L
    }
}
