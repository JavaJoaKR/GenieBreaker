package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** All in-app menus share the same Flutter remote-control UI. */
internal object TvMenus {
    private var optionCallback: ((Map<String, Boolean>) -> Unit)? = null
    private var callback: ((Int) -> Unit)? = null
    fun show(activity: Activity, title: String, items: List<String>, message: String = "", selected: Int = -1, action: (Int) -> Unit = {}) {
        optionCallback = null; callback = action
        activity.startActivityForResult(Intent(activity, TvMenuActivity::class.java)
            .putExtra("title", title).putExtra("items", items.toTypedArray())
            .putExtra("message", message).putExtra("selected", selected), 630)
    }
    fun cloneOptions(activity: Activity, title: String, values: Map<String, Boolean>, action: (Map<String, Boolean>) -> Unit) {
        callback = null; optionCallback = action
        val intent = Intent(activity, TvMenuActivity::class.java).putExtra("title", "$title · 듀얼앱 옵션").putExtra("clone_options", true)
        values.forEach { (key, value) -> intent.putExtra(key, value) }
        activity.startActivityForResult(intent, 630)
    }
    fun result(request: Int, result: Int, data: Intent?): Boolean {
        if (request != 630) return false
        val action = callback; val options = optionCallback; callback = null; optionCallback = null
        if (result == Activity.RESULT_OK) {
            if (data?.getBooleanExtra("clone_options", false) == true) options?.invoke(CloneOptions().values().mapValues { (key, fallback) -> data.getBooleanExtra(key, fallback) })
            else action?.invoke(data?.getIntExtra("choice", -1) ?: -1)
        }
        return true
    }
    fun notice(activity: Activity, text: String) = show(activity, "안내", listOf("닫기"), text)
}

class TvMenuActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "genie/ui").setMethodCallHandler { call, result ->
            when (call.method) {
                "ready" -> result.success(mapOf("title" to intent.getStringExtra("title"),
                    "items" to intent.getStringArrayExtra("items")?.toList(),
                    "message" to intent.getStringExtra("message"), "selected" to intent.getIntExtra("selected", -1),
                    "cloneOptions" to intent.getBooleanExtra("clone_options", false),
                    "options" to CloneOptions().values().mapValues { (key, fallback) -> intent.getBooleanExtra(key, fallback) }))
                "build" -> {
                    val values = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                    val answer = Intent().putExtra("clone_options", true)
                    CloneOptions().values().forEach { (key, fallback) -> answer.putExtra(key, values[key] as? Boolean ?: fallback) }
                    result.success(null); setResult(RESULT_OK, answer); finish()
                }
                "choose" -> { result.success(null); setResult(RESULT_OK, Intent().putExtra("choice", (call.arguments as Number).toInt())); finish() }
                "close" -> { result.success(null); finish() }
                else -> result.notImplemented()
            }
        }
    }
}
