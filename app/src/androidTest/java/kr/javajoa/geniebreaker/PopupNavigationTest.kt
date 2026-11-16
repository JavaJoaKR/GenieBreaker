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
        fun activePopup(): TvMenuActivity? {
            var popup: TvMenuActivity? = null
            instrumentation.runOnMainSync {
                popup = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<TvMenuActivity>().firstOrNull()
            }
            return popup
        }
        fun waitFor(title: String): TvMenuActivity? {
            repeat(40) {
                val popup = activePopup()
                if (popup != null && popup.title.toString() == title) return popup
                android.os.SystemClock.sleep(100)
            }
            return null
        }
        try {
            instrumentation.setInTouchMode(false)
            instrumentation.runOnMainSync {
                TvMenus.show(activity, "상위 테스트 메뉴", listOf("하위 메뉴", "작업 완료")) { index ->
                    if (index == 0) TvMenus.show(activity, "하위 테스트 메뉴", listOf("실행하지 않을 작업")) { childActions.incrementAndGet() }
                    else completed.incrementAndGet()
                }
            }
            val rootPopup = waitFor("상위 테스트 메뉴")
            assertNotNull("Popup host must appear", rootPopup)
            android.os.SystemClock.sleep(4000)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            assertSame("Child stays in the same popup", rootPopup, waitFor("하위 테스트 메뉴"))
            android.os.SystemClock.sleep(300)
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/genie-popup-depth.png").let { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { while (it.read() != -1) { } }
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            android.os.SystemClock.sleep(200)
            assertSame("Back restores parent in the same host", rootPopup, waitFor("상위 테스트 메뉴"))
            android.os.SystemClock.sleep(300)
            assertEquals(0, childActions.get())
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            repeat(20) { if (completed.get() == 0) android.os.SystemClock.sleep(100) }
            assertEquals("Parent focus and actions still work", 1, completed.get())
        } finally {
            instrumentation.runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<TvMenuActivity>().forEach { it.finish() }
                activity.finish()
            }
        }
    }
}
