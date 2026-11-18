package kr.javajoa.geniebreaker

import android.content.Context

internal fun tvPreferences(context: Context) =
    context.getSharedPreferences("tv_controls", Context.MODE_PRIVATE)

internal val buttonActions = linkedMapOf(
    "home" to "홈 화면",
    "back" to "뒤로",
    "apps" to "앱 목록",
    "accessibility" to "접근성 설정"
)

/** The service and UI share the existing preference keys, including pre-registration bindings. */
internal class RemoteBindings(context: Context) {
    private val prefs = tvPreferences(context)

    fun action(key: Int): String? = prefs.getString("button.$key", null)?.takeIf(::validAction)

    fun register(key: Int) {
        prefs.edit().putBoolean("buttonRegistered.$key", true).apply()
    }

    fun set(key: Int, action: String?, label: String?) {
        val update = prefs.edit().putString("button.$key", action).putString("buttonLabel.$key", label)
        if (action == null) update.remove("buttonRegistered.$key")
        update.apply()
    }

    fun rows(): List<Map<String, Any>> {
        val values = prefs.all
        return values.keys.mapNotNull { pref ->
            when {
                pref.startsWith("button.") || pref.startsWith("buttonRegistered.") ->
                    pref.substringAfter('.').toIntOrNull()
                else -> null
            }
        }.distinct().sorted().mapNotNull { key ->
            val label = buttonLabel(key) ?: return@mapNotNull null
            val action = (values["button.$key"] as? String)?.takeIf(::validAction)
            val summary = if (action == null) "동작 미지정" else
                buttonActions[action] ?: values["buttonLabel.$key"] as? String ?: action.removePrefix("app:")
            mapOf("key" to key, "label" to label, "action" to summary)
        }
    }

    private fun validAction(value: String): Boolean =
        value in buttonActions || (value.startsWith("app:") && value.removePrefix("app:").isNotBlank())
}
