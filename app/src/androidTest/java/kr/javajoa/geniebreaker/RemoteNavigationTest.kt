package kr.javajoa.geniebreaker

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class RemoteNavigationTest {
    @Test fun sidebarAndBluetoothColumnsKeepPredictableFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun find(view: View, title: String): Button? {
            if (view is Button && view.text.toString() == title) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), title)?.let { return it }
            return null
        }
        fun key(code: Int) { instrumentation.sendKeyDownUpSync(code); instrumentation.waitForIdleSync(); instrumentation.runOnMainSync { assertNotNull(activity.currentFocus); assertTrue(activity.currentFocus!!.isShown) } }
        try {
            instrumentation.setInTouchMode(false)
            val tab = find(activity.findViewById(android.R.id.content), "블루투스")!!
            instrumentation.runOnMainSync { tab.requestFocus() }
            key(KeyEvent.KEYCODE_DPAD_UP)
            instrumentation.runOnMainSync { assertSame(tab, activity.currentFocus) }
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            instrumentation.runOnMainSync { assertEquals("검색 중지", (activity.currentFocus as Button).text.toString()) }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.runOnMainSync { assertTrue(activity.currentFocus!!.contentDescription.toString().contains("WH-1000XM5")) }
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            instrumentation.runOnMainSync { assertTrue(activity.currentFocus!!.contentDescription.toString().contains("거실 헤드폰")) }
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            instrumentation.runOnMainSync { assertSame(tab, activity.currentFocus) }
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            key(KeyEvent.KEYCODE_BACK)
            instrumentation.runOnMainSync { assertSame(tab, activity.currentFocus) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
