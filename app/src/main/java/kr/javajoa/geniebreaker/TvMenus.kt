package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterActivityLaunchConfigs.BackgroundMode
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.lang.ref.WeakReference

internal class MenuPage(
    val title: String,
    val items: List<String> = emptyList(),
    val message: String = "",
    val selected: Int = -1,
    val action: (Int) -> Unit = {},
    val optionsAction: ((Map<String, Boolean>) -> Unit)? = null,
    var options: Map<String, Boolean> = emptyMap(),
    var focused: Int = 0,
    var focusKey: String = "",
    val id: String = java.util.UUID.randomUUID().toString()
)

/** One translucent host owns a menu stack; backing out never executes an action. */
internal object TvMenus {
    private var pending: MenuPage? = null
    private var opener = WeakReference<android.view.View>(null)
    private var host = WeakReference<TvMenuActivity>(null)
    fun show(activity: Activity, title: String, items: List<String>, message: String = "", selected: Int = -1, action: (Int) -> Unit = {}) {
        val diagnostic = if (!title.startsWith("문제 기록") && ErrorCodes.isError("$title $message") && !message.contains("[GB-")) "${message}\n[${Diagnostics.record(activity, "$title: $message")}]" else message
        open(activity, MenuPage(title, items, diagnostic, selected = selected, action = action))
    }
    fun cloneOptions(activity: Activity, title: String, values: Map<String, Boolean>, action: (Map<String, Boolean>) -> Unit) {
        open(activity, MenuPage("듀얼앱 만들기", message = title, optionsAction = action, options = values))
    }
    private fun open(activity: Activity, page: MenuPage) {
        val active = host.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        if (active != null) { active.push(page); return }
        opener = WeakReference(activity.currentFocus)
        pending = page
        activity.startActivityForResult(Intent(activity, TvMenuActivity::class.java).putExtra("background_mode", "transparent"), 630)
    }
    internal fun takePending(): MenuPage? = pending.also { pending = null }
    internal fun attach(activity: TvMenuActivity) { host = WeakReference(activity) }
    internal fun detach(activity: TvMenuActivity) { if (host.get() === activity) host.clear() }
    fun result(request: Int, result: Int, data: Intent?): Boolean {
        if (request != 630) return false
        opener.get()?.let { view -> view.post { if (view.isShown && view.isFocusable && view.isEnabled) view.requestFocus() } }
        opener.clear()
        return true
    }
    fun notice(activity: Activity, text: String) = show(activity, "안내", listOf("닫기"), text)
}

class TvMenuActivity : FlutterActivity() {
    private val pages = mutableListOf<MenuPage>()
    private var channel: MethodChannel? = null
    override fun getBackgroundMode() = BackgroundMode.transparent
    override fun onCreate(savedInstanceState: Bundle?) {
        TvMenus.takePending()?.let { pages.add(it) }
        super.onCreate(savedInstanceState)
        // Callbacks cannot survive process death. Close safely instead of offering inert actions.
        if (pages.isEmpty()) { finish(); return }
        TvMenus.attach(this)
        title = pages.last().title
    }
    internal fun push(page: MenuPage) { pages.add(page); publish() }
    private fun snapshot(): Map<String, Any> {
        val page = pages.last()
        return mapOf("title" to page.title, "items" to page.items, "message" to page.message,
            "selected" to page.selected, "focus" to page.focused, "focusKey" to page.focusKey, "pageId" to page.id,
            "breadcrumbs" to pages.dropLast(1).map { it.title }, "depth" to pages.size,
            "cloneOptions" to (page.optionsAction != null), "options" to page.options)
    }
    private fun publish() { if (pages.isNotEmpty()) { title = pages.last().title; channel?.invokeMethod("page", snapshot()) } }
    private fun back() { if (pages.size > 1) { pages.removeAt(pages.lastIndex); publish() } else finish() }
    @Deprecated("Android TV back key compatibility")
    override fun onBackPressed() = back()
    override fun onDestroy() { TvMenus.detach(this); channel?.setMethodCallHandler(null); pages.clear(); super.onDestroy() }
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "genie/ui").apply { setMethodCallHandler { call, result ->
            if (pages.isEmpty()) { result.error("closed", "메뉴가 닫혔습니다", null); return@setMethodCallHandler }
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
                    val chosen = page.options.mapValues { (key, fallback) -> values[key] as? Boolean ?: fallback }
                    page.optionsAction?.invoke(chosen)
                    result.success(null)
                    if (pages.lastOrNull() === page) finish()
                }
                "choose" -> {
                    val index = (call.arguments as? Number)?.toInt() ?: -1
                    if (index !in page.items.indices) { result.error("choice", "잘못된 선택", null); return@setMethodCallHandler }
                    page.focused = index
                    try { page.action(index) } catch (error: Exception) { TvMenus.notice(this@TvMenuActivity, error.message ?: "요청을 처리하지 못했습니다") }
                    val label = page.items[index]
                    result.success(if (label == "복사") "복사했습니다" else null)
                    if (pages.lastOrNull() === page) when (label) {
                        "닫기", "취소", "나중에" -> back()
                        "복사" -> Unit
                        else -> finish()
                    }
                }
                "back" -> { result.success(null); back() }
                "close" -> { result.success(null); finish() }
                else -> result.notImplemented()
            }
        } }
    }
}
