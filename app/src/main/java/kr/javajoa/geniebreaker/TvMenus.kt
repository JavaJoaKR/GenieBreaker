package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** All in-app menus share the same Flutter remote-control UI. */
internal object TvMenus {
    private var callback: ((Int) -> Unit)? = null
    fun show(activity: Activity, title: String, items: List<String>, message: String = "", selected: Int = -1, action: (Int) -> Unit = {}) {
        callback = action
        activity.startActivityForResult(Intent(activity, TvMenuActivity::class.java)
            .putExtra("title", title).putExtra("items", items.toTypedArray())
            .putExtra("message", message).putExtra("selected", selected), 630)
    }
    fun result(request: Int, result: Int, data: Intent?): Boolean {
        if (request != 630) return false
        val action = callback; callback = null
        if (result == Activity.RESULT_OK) action?.invoke(data?.getIntExtra("choice", -1) ?: -1)
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
                    "message" to intent.getStringExtra("message"), "selected" to intent.getIntExtra("selected", -1)))
                "choose" -> { result.success(null); setResult(RESULT_OK, Intent().putExtra("choice", (call.arguments as Number).toInt())); finish() }
                "close" -> { result.success(null); finish() }
                else -> result.notImplemented()
            }
        }
    }
}
