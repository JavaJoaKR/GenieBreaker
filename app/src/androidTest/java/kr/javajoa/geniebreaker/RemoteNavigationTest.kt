package kr.javajoa.geniebreaker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import io.flutter.embedding.android.FlutterView
import org.junit.Assert.*
import org.junit.Test

/** Direction and focus contracts are exercised in Flutter widget tests, their actual focus owner. */
class RemoteNavigationTest {
    @Test fun allScreensShareOneFlutterSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun count(view: View, predicate: (View) -> Boolean): Int = (if (predicate(view)) 1 else 0) +
            if (view is ViewGroup) (0 until view.childCount).sumOf { count(view.getChildAt(it), predicate) } else 0
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val root = activity.findViewById<View>(android.R.id.content)
                assertEquals(1, count(root) { it is FlutterView })
                assertEquals(0, count(root) { it is Button })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
