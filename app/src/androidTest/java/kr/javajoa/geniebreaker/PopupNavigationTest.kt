package kr.javajoa.geniebreaker

import androidx.test.platform.app.InstrumentationRegistry
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Assert.*
import org.junit.Test

class PopupNavigationTest {
    @Test fun backRestoresParentFocusAndNeverExecutesChild() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val activity = android.app.Activity()
            var snapshot = emptyMap<String, Any>()
            var dismissed = false
            var actions = 0
            val menus = MenuController(activity, { snapshot = it }, { dismissed = true })
            val result = object : MethodChannel.Result {
                override fun success(value: Any?) { }
                override fun error(code: String, message: String?, details: Any?) { fail(message) }
                override fun notImplemented() { fail("Unexpected method") }
            }
            menus.push(MenuPage("부모", listOf("자식", "완료"), action = { actions++ }))
            menus.handle(MethodCall("focus", 1), result)
            menus.push(MenuPage("자식", listOf("실행하지 않을 작업"), action = { actions += 100 }))
            menus.handle(MethodCall("back", null), result)
            assertEquals("부모", snapshot["title"])
            assertEquals(1, snapshot["focus"])
            assertEquals(0, actions)
            assertFalse(dismissed)
            menus.handle(MethodCall("choose", 1), result)
            assertEquals(1, actions)
            assertTrue(dismissed)
        }
    }
}
