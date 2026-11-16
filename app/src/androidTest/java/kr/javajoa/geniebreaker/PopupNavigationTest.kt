package kr.javajoa.geniebreaker

import android.content.Intent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PopupNavigationTest {
    @Test fun backReturnsToParentWithoutExecutingAnotherAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val completed = AtomicInteger()
        val childActions = AtomicInteger()
        fun waitFor(text: String): Boolean {
            repeat(40) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                if (root != null) {
                    val found = root.findAccessibilityNodeInfosByText(text).isNotEmpty()
                    root.recycle()
                    if (found) return true
                }
                android.os.SystemClock.sleep(100)
            }
            return false
        }
        try {
            instrumentation.setInTouchMode(false)
            instrumentation.runOnMainSync {
                TvMenus.show(activity, "상위 테스트 메뉴", listOf("하위 메뉴", "작업 완료")) { index ->
                    if (index == 0) TvMenus.show(activity, "하위 테스트 메뉴", listOf("실행하지 않을 작업")) { childActions.incrementAndGet() }
                    else completed.incrementAndGet()
                }
            }
            assertTrue("Popup must render", waitFor("상위 테스트 메뉴"))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue("Child stays in the same popup", waitFor("하위 테스트 메뉴"))
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertTrue("Back restores parent", waitFor("상위 테스트 메뉴"))
            assertEquals(0, childActions.get())
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            repeat(20) { if (completed.get() == 0) android.os.SystemClock.sleep(100) }
            assertEquals("Parent focus and actions still work", 1, completed.get())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
