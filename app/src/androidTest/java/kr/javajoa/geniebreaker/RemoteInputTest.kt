package kr.javajoa.geniebreaker

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** InputReader -> Android window -> Flutter focus and activation, using real USB key scan codes. */
class RemoteInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun awaitMenuValue(activity: MainActivity, key: String, expected: String?) {
        // A focus acknowledgement comes from Dart after rendering. Android idle
        // or a published menu alone does not mean Flutter is ready for the next key.
        val field = MainActivity::class.java.getDeclaredField("menus").apply { isAccessible = true }
        val deadline = SystemClock.uptimeMillis() + 8000
        var actual: String? = null
        do {
            instrumentation.runOnMainSync {
                actual = (field.get(activity) as MenuController).current()?.get(key) as? String
            }
            if (actual == expected) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("Remote menu $key", expected, actual)
    }
    private fun awaitMenu(activity: MainActivity, title: String?) = awaitMenuValue(activity, "title", title)
    private fun awaitFocus(activity: MainActivity, key: String) = awaitMenuValue(activity, "focusKey", key)
    private fun awaitChoice(choice: AtomicInteger, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (choice.get() == -1 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertEquals("Direction and OK must activate exactly the requested row", expected, choice.get())
    }

    private fun unmappedRemoteKey(activity: MainActivity, scanCode: Int, expectedHandled: Boolean = true,
                                  source: Int = InputDevice.SOURCE_DPAD) {
        val downTime = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync {
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val event = KeyEvent(downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_UNKNOWN,
                    0, 0, -1, scanCode, 0, source)
                val handled = activity.dispatchKeyEvent(event)
                if (expectedHandled) assertTrue("The activity must handle scan code $scanCode", handled)
            }
        }
    }

    @Test fun usbRemoteNavigatesTabsAndPopupAfterNativeWindowReturns() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val pipes = instrumentation.uiAutomation.executeShellCommandRw("uinput -")
        val input = ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])
        val prefs = tvPreferences(activity)
        val red = KeyEvent.KEYCODE_PROG_RED
        val shortcutScan = 0x2e8
        val shortcutBinding = 0x10000 + shortcutScan
        fun send(line: String) { input.write((line + "\n").toByteArray()); input.flush() }
        fun press(code: Int) {
            send("""{"id":1,"command":"inject","events":[1,$code,1,0,0,0]}""")
            SystemClock.sleep(70)
            send("""{"id":1,"command":"inject","events":[1,$code,0,0,0,0]}""")
            SystemClock.sleep(220)
        }
        try {
            instrumentation.setInTouchMode(true)
            send("""{"id":1,"command":"register","name":"Genie D-pad Regression","vid":2567,"pid":7043,"bus":"usb","configuration":[{"type":100,"data":[1]},{"type":101,"data":[103,108,105,106,28,314,353,158]}]}""")
            SystemClock.sleep(2500)
            assertFalse("Connecting a remote must preserve the Flutter activity", activity.isDestroyed)
            // Up clamps at the first tab. Five down presses must reach button mapping.
            press(103)
            repeat(5) { press(108) }
            press(106)
            press(314) // A controller can report Android BUTTON_SELECT for OK.
            awaitMenu(activity, "버튼 추가")
            awaitFocus(activity, "item-0")
            press(158)
            awaitMenu(activity, null)
            unmappedRemoteKey(activity, 0x160) // Linux KEY_OK without an Android layout entry.
            awaitMenu(activity, "버튼 추가")
            awaitFocus(activity, "item-0")
            instrumentation.runOnMainSync {
                val down = SystemClock.uptimeMillis()
                assertTrue(activity.dispatchKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN, red, 0)))
                assertTrue(activity.dispatchKeyEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, red, 0)))
            }
            awaitMenu(activity, "빨간색")
            awaitFocus(activity, "item-0")
            assertTrue(prefs.getBoolean("buttonRegistered.$red", false))
            assertFalse(prefs.contains("button.$red"))
            press(158)
            awaitMenu(activity, null)

            press(314)
            awaitMenu(activity, "버튼 추가")
            awaitFocus(activity, "item-0")
            unmappedRemoteKey(activity, 0x274, expectedHandled = false) // Linux KEY_UNMUTE remains with Android.
            val instructions = "추가할 리모컨 버튼을 누르세요. TV 본체 전원, 음량, 화면 이동 버튼은 기본 조작에 사용됩니다."
            awaitMenuValue(activity, "message", "$instructions\n\n이 입력은 현재 매핑할 수 없습니다.\nUNKNOWN (0) / scan 628 / source 201 / meta 0")
            awaitFocus(activity, "item-0")
            unmappedRemoteKey(activity, shortcutScan, source = InputDevice.SOURCE_HDMI)
            awaitMenu(activity, "리모컨 버튼 $shortcutScan")
            awaitFocus(activity, "item-0")
            press(108)
            awaitFocus(activity, "item-1")
            press(314)
            awaitMenu(activity, null)
            assertEquals("home", prefs.getString("button.$shortcutBinding", null))

            val choice = AtomicInteger(-1)
            instrumentation.runOnMainSync {
                TvMenus.show(activity, "리모컨 회귀 검사", listOf("첫 번째", "두 번째", "세 번째")) { choice.set(it) }
            }
            awaitMenu(activity, "리모컨 회귀 검사")
            awaitFocus(activity, "item-0")
            unmappedRemoteKey(activity, 108) // KEY_DOWN without an Android layout entry.
            awaitFocus(activity, "item-1")
            // A native settings/keyboard window temporarily owns the Android window focus.
            var dialog: android.app.AlertDialog? = null
            instrumentation.runOnMainSync {
                dialog = android.app.AlertDialog.Builder(activity).setMessage("시스템 화면 복귀 검사").setPositiveButton("닫기", null).show()
            }
            SystemClock.sleep(300)
            instrumentation.runOnMainSync { dialog?.dismiss() }
            SystemClock.sleep(350)
            press(353)
            awaitChoice(choice, 1)
            assertFalse(activity.isFinishing)
        } catch (error: AssertionError) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/genie-remote-failure.png"
            )).use { it.readBytes() }
            throw error
        } finally {
            prefs.edit().remove("buttonRegistered.$red").remove("buttonRegistered.$shortcutBinding")
                .remove("button.$shortcutBinding").remove("buttonLabel.$shortcutBinding").apply()
            input.close()
            pipes[0].close()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
