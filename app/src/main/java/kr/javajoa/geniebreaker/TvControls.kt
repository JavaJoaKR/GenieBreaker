package kr.javajoa.geniebreaker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

internal class TvControls(private val owner: Activity, private val pageId: () -> String?, private val changed: () -> Unit) {
    private val prefs = tvPreferences(owner)
    private val pm = owner.packageManager
    private var capturePage: String? = null
    private var state = emptyMap<String, Any>()
    private val capture: (Int) -> Boolean = { key ->
        if (capturePage != null && capturePage == pageId() && owner.hasWindowFocus()) {
            edit(key)
            true
        } else false
    }
    fun resume() { RemoteCapture.choose = capture; StartupWakeService.restore(owner); refresh() }
    fun pause() { if (RemoteCapture.choose === capture) RemoteCapture.choose = null }
    fun snapshot(): Map<String, Any> = state
    private fun refresh() {
        val manager = owner.getSystemService(AccessibilityManager::class.java)
        val active = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info = it.resolveInfo.serviceInfo
            ComponentName(info.packageName, info.name) == ComponentName(owner, RemoteButtonService::class.java)
        }
        val bindings = prefs.all.keys.mapNotNull { it.removePrefix("button.").toIntOrNull() }.sorted().mapNotNull { key ->
            val label = buttonLabel(key) ?: return@mapNotNull null
            val action = prefs.getString("button.$key", null) ?: return@mapNotNull null
            mapOf("key" to key, "label" to label, "action" to (buttonActions[action] ?: prefs.getString("buttonLabel.$key", null) ?: action.removePrefix("app:")))
        }
        val defaultHome = isDefaultHome(owner)
        val hasOverlay = StartupApps.allowed(owner)
        state = mapOf("mappingActive" to active, "bindings" to bindings, "startupName" to (prefs.getString("startupLabel", null) ?: ""),
            "needsOverlay" to (Build.VERSION.SDK_INT >= 29), "canStart" to (defaultHome || hasOverlay),
            "hasOverlay" to hasOverlay, "defaultHome" to defaultHome, "wake" to prefs.getBoolean("wake", false))
        changed()
    }
    fun accessibility() {
        if (!openScreen(owner, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            TvMenus.notice(owner, "이 TV에서는 접근성 설정을 직접 열 수 없습니다. TV 설정에서 접근성을 여세요.")
    }
    fun chooseButton() {
        val keys = remoteButtons.keys.toList()
        TvMenus.show(owner, "리모컨 버튼 선택", remoteButtons.values.toList(), "지정할 버튼을 직접 누르거나 목록에서 선택하세요.") { edit(keys[it]) }
        capturePage = pageId()
    }
    fun edit(key: Int) {
        val title = buttonLabel(key) ?: return
        val existing = prefs.getString("button.$key", null)
        val actions = buttonActions.keys.toList()
        val labels = listOf("앱 실행") + buttonActions.values + if (existing == null) emptyList() else listOf("맵핑 해제")
        TvMenus.show(owner, title, labels, "버튼을 한 번 눌렀을 때 실행할 동작", when {
            existing == null -> -1
            existing.startsWith("app:") -> 0
            else -> actions.indexOf(existing) + 1
        }) { index ->
            when {
                index == 0 -> chooseApp("버튼으로 실행할 앱") { pkg, name -> bind(key, "app:$pkg", name) }
                index <= actions.size -> bind(key, actions[index - 1], null)
                else -> bind(key, null, null)
            }
        }
    }
    private fun bind(key: Int, action: String?, label: String?) {
        prefs.edit().putString("button.$key", action).putString("buttonLabel.$key", label).apply()
        refresh()
    }
    private fun chooseApp(title: String, apply: (String, String) -> Unit) {
        val entries = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0)
        }.filter { it.activityInfo.exported && it.activityInfo.packageName != owner.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .sortedWith(compareBy({ it.second.lowercase() }, { it.first }))
        if (entries.isEmpty()) { TvMenus.notice(owner, "실행할 수 있는 앱이 없습니다."); return }
        TvMenus.show(owner, title, entries.map { "${it.second}\n${it.first}" }) { i ->
            val (pkg, name) = entries[i]
            apply(pkg, name)
        }
    }
    fun chooseStartup() = chooseApp("시작 앱 선택", ::selectStartup)
    fun selectStartup(pkg: String, label: String) {
        if (launchIntent(owner, pkg) == null) {
            TvMenus.notice(owner, "실행할 수 없는 앱입니다.")
            return
        }
        StartupApps.select(owner, pkg, label)
        refresh()
    }
    fun startupPermission() {
        if (!StartupApps.permissionSettings(owner))
            TvMenus.notice(owner, "이 TV에서는 권한 설정을 바로 열 수 없습니다. 설정에서 ‘다른 앱 위에 표시’를 확인하세요.")
    }
    fun toggleWake() {
        val enabled = !prefs.getBoolean("wake", false)
        if (enabled && !StartupApps.allowed(owner)) { startupPermission(); return }
        prefs.edit().putBoolean("wake", enabled).apply()
        if (!enabled) owner.stopService(Intent(owner, StartupWakeService::class.java))
        else if (!StartupWakeService.restore(owner)) {
            prefs.edit().putBoolean("wake", false).apply()
            TvMenus.notice(owner, "이 TV에서 대기 해제 감지를 시작할 수 없습니다.")
        }
        refresh()
    }
    fun testStartup() {
        if (!launchPackage(owner, prefs.getString("startup", null).orEmpty()))
            TvMenus.notice(owner, "이 앱을 실행할 수 없습니다. 설치 상태를 확인하거나 다른 앱을 선택하세요.")
    }
    fun clearStartup() { StartupApps.clear(owner); refresh() }
}
