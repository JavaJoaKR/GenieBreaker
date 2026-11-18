package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.Intent
import android.os.Build

internal class TvControls(
    private val owner: Activity,
    private val pageId: () -> String?,
    private val closeMenu: () -> Unit,
    private val changed: () -> Unit
) {
    private val prefs = tvPreferences(owner)
    private val bindings = RemoteBindings(owner)
    private val remoteAccessibility = RemoteAccessibility(owner, ::refresh)
    private var capturePage: String? = null
    private var state = emptyMap<String, Any>()
    private val capture: (Int) -> Boolean = { key ->
        if (capturePage != null && capturePage == pageId() && owner.hasWindowFocus()) {
            capturePage = null
            bindings.register(key)
            closeMenu()
            refresh()
            true
        } else false
    }
    fun resume() {
        RemoteCapture.choose = capture
        remoteAccessibility.start()
        StartupWakeService.restore(owner)
        refresh()
    }
    fun pause() {
        remoteAccessibility.stop()
        if (RemoteCapture.choose === capture) RemoteCapture.choose = null
    }
    fun snapshot(): Map<String, Any> = state
    fun refresh() {
        val defaultHome = isDefaultHome(owner)
        val hasOverlay = StartupApps.allowed(owner)
        val next = mapOf(
            "mappingStatus" to remoteAccessibility.status(),
            "bindings" to bindings.rows(),
            "startupName" to prefs.getString("startupLabel", "").orEmpty(),
            "needsOverlay" to (Build.VERSION.SDK_INT >= 29),
            "canStart" to (defaultHome || hasOverlay),
            "hasOverlay" to hasOverlay,
            "defaultHome" to defaultHome,
            "wake" to StartupApps.wakeEnabled(owner)
        )
        if (next != state) {
            state = next
            changed()
        }
    }
    fun accessibility() {
        if (!openAccessibilityScreen(owner))
            TvMenus.notice(owner, "이 TV에서는 접근성 설정을 직접 열 수 없습니다. TV 설정에서 접근성을 여세요.")
    }
    fun mappingSettings() {
        if (Build.VERSION.SDK_INT < 33 || state["mappingStatus"] == "active") {
            accessibility()
            return
        }
        TvMenus.show(
            owner,
            "버튼 맵핑 설정",
            listOf("접근성 설정", "GenieBreaker 앱 정보", "닫기"),
            "접근성에서 ‘GenieBreaker 버튼 맵핑’을 켜세요. OS가 제한된 설정을 표시하면 앱 정보의 더보기에서 ‘제한된 설정 허용’을 확인하세요."
        ) { index ->
            when (index) {
                0 -> accessibility()
                1 -> {
                    if (!openAppDetails(owner, owner.packageName)) TvMenus.notice(owner, "TV 설정의 앱 목록에서 GenieBreaker를 선택하세요.")
                }
            }
        }
    }
    fun chooseButton() {
        TvMenus.show(owner, "버튼 추가", listOf("취소"), "추가할 리모컨 버튼을 누르세요.")
        capturePage = pageId()
    }
    fun edit(key: Int) {
        val title = buttonLabel(key) ?: return
        val existing = bindings.action(key)
        val actions = buttonActions.keys.toList()
        val labels = listOf("앱 실행") + buttonActions.values + listOf("버튼 삭제")
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
        bindings.set(key, action, label)
        refresh()
    }
    private fun chooseApp(title: String, apply: (String, String) -> Unit) {
        val entries = launchableApps(owner)
        if (entries.isEmpty()) { TvMenus.notice(owner, "실행할 수 있는 앱이 없습니다."); return }
        TvMenus.show(owner, title, entries.map { "${it.label}\n${it.pkg}" }) { i ->
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
        StartupWakeService.restore(owner)
        refresh()
    }
    fun startupPermission() {
        if (!StartupApps.permissionSettings(owner))
            TvMenus.notice(owner, "이 TV에서는 권한 설정을 바로 열 수 없습니다. 설정에서 ‘다른 앱 위에 표시’를 확인하세요.")
    }
    fun toggleWake() {
        val enabled = !StartupApps.wakeEnabled(owner)
        prefs.edit().putBoolean("wake", enabled).apply()
        if (!enabled) owner.stopService(Intent(owner, StartupWakeService::class.java))
        else if (!StartupApps.allowed(owner)) startupPermission()
        else if (!StartupWakeService.restore(owner)) {
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
