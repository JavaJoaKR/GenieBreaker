package kr.javajoa.geniebreaker

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** InputReader -> Android window -> Flutter focus and activation, using real USB key scan codes. */
class RemoteInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun awaitMenu(activity: MainActivity, title: String?) {
        // Observe the real Android menu controller reached by Flutter selection.
        // This avoids depending on whether the test runner exposes Flutter semantics.
        val field = MainActivity::class.java.getDeclaredField("menus").apply { isAccessible = true }
        val deadline = SystemClock.uptimeMillis() + 8000
        var actual: String? = null
        do {
            instrumentation.runOnMainSync {
                actual = (field.get(activity) as MenuController).current()?.get("title") as? String
            }
            if (actual == title) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("Remote must reach the requested popup", title, actual)
    }
    private fun awaitChoice(choice: AtomicInteger, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (choice.get() == -1 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertEquals("Direction and OK must activate exactly the requested row", expected, choice.get())
    }

    @Test fun usbRemoteNavigatesTabsAndPopupAfterNativeWindowReturns() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val pipes = instrumentation.uiAutomation.executeShellCommandRw("uinput -")
        val input = ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])
        fun send(line: String) { input.write((line + "\n").toByteArray()); input.flush() }
        fun press(code: Int) {
            send("""{"id":1,"command":"inject","events":[1,$code,1,0,0,0]}""")
            SystemClock.sleep(70)
            send("""{"id":1,"command":"inject","events":[1,$code,0,0,0,0]}""")
            SystemClock.sleep(220)
        }
        try {
            instrumentation.setInTouchMode(true)
            send("""{"id":1,"command":"register","name":"Genie D-pad Regression","vid":2567,"pid":7043,"bus":"usb","configuration":[{"type":100,"data":[1]},{"type":101,"data":[103,108,105,106,28,353,158]}]}""")
            SystemClock.sleep(2500)
            assertFalse("Connecting a remote must preserve the Flutter activity", activity.isDestroyed)
            // Up clamps at the first tab. Five down presses must reach button mapping.
            press(103)
            repeat(5) { press(108) }
            press(353) // KEY_SELECT -> Android DPAD_CENTER, distinct from keyboard Enter.
            SystemClock.sleep(250)
            press(106)
            press(108)
            press(353)
            awaitMenu(activity, "리모컨 버튼 선택")
            press(158)
            awaitMenu(activity, null)
            press(353)
            awaitMenu(activity, "리모컨 버튼 선택")
            press(158)
            awaitMenu(activity, null)

            val choice = AtomicInteger(-1)
            instrumentation.runOnMainSync {
                TvMenus.show(activity, "리모컨 회귀 검사", listOf("첫 번째", "두 번째", "세 번째")) { choice.set(it) }
            }
            awaitMenu(activity, "리모컨 회귀 검사")
            press(108)
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
            val logs = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("logcat -d -s GenieFocusTest flutter")).use {
                it.bufferedReader().readText().lines().filter { line -> line.contains("GenieFocusTest") }.takeLast(35).joinToString("\n")
            }
            throw AssertionError(error.message + "\n" + logs, error)
        } finally {
            input.close()
            pipes[0].close()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
