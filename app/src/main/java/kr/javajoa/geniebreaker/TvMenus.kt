package kr.javajoa.geniebreaker

import android.app.Activity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference

internal class MenuPage(
    val title: String, val items: List<String> = emptyList(), val message: String = "",
    val selected: Int = -1, val action: (Int) -> Unit = {},
    val optionsAction: ((Map<String, Boolean>) -> Unit)? = null,
    var options: Map<String, Boolean> = emptyMap(), var focused: Int = 0, var focusKey: String = "",
    val id: String = java.util.UUID.randomUUID().toString()
)

/** All menus stay in the main engine. No popup Activity or second focus owner. */
internal object TvMenus {
    private var activity = WeakReference<Activity>(null)
    private var controller: MenuController? = null
    fun attach(owner: Activity, menus: MenuController) { activity = WeakReference(owner); controller = menus }
    fun detach(owner: Activity) { if (activity.get() === owner) { activity.clear(); controller = null } }
    fun show(owner: Activity, title: String, items: List<String>, message: String = "", selected: Int = -1, action: (Int) -> Unit = {}) {
        if (activity.get() !== owner || owner.isDestroyed) return
        val diagnostic = if (!title.startsWith("문제 기록") && ErrorCodes.isError("$title $message") && !message.contains("[GB-")) "$message\n[${Diagnostics.record(owner, "$title: $message")}]" else message
        controller?.push(MenuPage(title, items, diagnostic, selected, action))
    }
    fun cloneOptions(owner: Activity, title: String, values: Map<String, Boolean>, action: (Map<String, Boolean>) -> Unit) {
        if (activity.get() === owner) controller?.push(MenuPage("듀얼앱 만들기", message = title, optionsAction = action, options = values))
    }
    fun notice(owner: Activity, text: String) = show(owner, "안내", listOf("닫기"), text)
}

internal class MenuController(private val activity: Activity, private val publish: (Map<String, Any>) -> Unit, private val dismiss: () -> Unit) {
    private val pages = mutableListOf<MenuPage>()
    fun push(page: MenuPage) { pages.add(page); publish(snapshot()) }
    fun snapshot(): Map<String, Any> {
        val page = pages.last()
        return mapOf("title" to page.title, "items" to page.items, "message" to page.message,
            "selected" to page.selected, "focus" to page.focused, "focusKey" to page.focusKey, "pageId" to page.id,
            "breadcrumbs" to pages.dropLast(1).map { it.title }, "depth" to pages.size,
            "cloneOptions" to (page.optionsAction != null), "options" to page.options)
    }
    private fun back() { pages.removeLastOrNull(); if (pages.isEmpty()) dismiss() else publish(snapshot()) }
    fun republish() { if (pages.isNotEmpty()) publish(snapshot()) }
    fun clear() { pages.clear() }
    fun handle(call: MethodCall, result: MethodChannel.Result): Boolean {
        if (pages.isEmpty()) return false
        val page = pages.last()
        when (call.method) {
            "ready" -> result.success(snapshot())
            "focus" -> { if (call.arguments is String) page.focusKey = call.arguments as String else { page.focused = (call.arguments as? Number)?.toInt() ?: 0; page.focusKey = "" }; result.success(null) }
            "options" -> {
                val values = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                page.options = page.options.mapValues { (key, old) -> values[key] as? Boolean ?: old }; result.success(null)
            }
            "build" -> {
                val values = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                try { page.optionsAction?.invoke(page.options.mapValues { (key, old) -> values[key] as? Boolean ?: old }) }
                catch (error: Exception) { TvMenus.notice(activity, "요청 실패 [${Diagnostics.record(activity, "Clone request", error)}]") }
                result.success(null)
                if (pages.lastOrNull() === page) { clear(); dismiss() }
            }
            "choose" -> {
                val index = (call.arguments as? Number)?.toInt() ?: -1
                if (index !in page.items.indices) { result.error("choice", "잘못된 선택 [GB-UI-0001]", null); return true }
                page.focused = index
                try { page.action(index) } catch (error: Exception) { TvMenus.notice(activity, "요청 실패 [${Diagnostics.record(activity, "Menu request", error)}]") }
                result.success(null)
                if (pages.lastOrNull() === page) when (page.items[index]) {
                    "닫기", "취소", "나중에" -> back()
                    "복사", "기록 복사" -> Unit
                    else -> { clear(); dismiss() }
                }
            }
            "back" -> { result.success(null); back() }
            "close" -> { result.success(null); clear(); dismiss() }
            else -> return false
        }
        return true
    }
}
