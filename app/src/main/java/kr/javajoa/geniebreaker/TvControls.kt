package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.KeyEvent

private const val CAPTURE_INSTRUCTIONS =
    "추가할 리모컨 버튼을 누르세요. 전원·볼륨 등 TV가 직접 처리하는 버튼은 입력이 전달되지 않을 수 있습니다."

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
        if (isCapturing()) {
            capturePage = null
            bindings.register(key)
            closeMenu()
            edit(key)
            refresh()
            true
        } else false
    }

    private fun isCapturing() = capturePage != null && capturePage == pageId() && owner.hasWindowFocus()

    private fun showUnsupportedButton(event: KeyEvent) {
        val page = capturePage ?: return
        if (!isCapturing() || event.keyCode == KeyEvent.KEYCODE_BACK || remoteNavigationCommand(event) != null ||
            (!isRemoteInput(event) && event.isPrintingKey)) return
        val key = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        val source = event.source.toString(16)
        val modifiers = event.metaState.toString(16)
        TvMenus.updateMessage(
            owner, page,
            "$CAPTURE_INSTRUCTIONS\n\n등록할 수 없는 입력 · $key (${event.keyCode}) / scan ${event.scanCode} / source $source / meta $modifiers"
        )
    }

    fun resume() {
        RemoteCapture.choose = capture
        RemoteCapture.observeUnclaimed = ::showUnsupportedButton
        remoteAccessibility.start()
        StartupWakeService.restore(owner)
        refresh()
    }
    fun pause() {
        remoteAccessibility.stop()
        if (RemoteCapture.choose === capture) {
            RemoteCapture.choose = null
            RemoteCapture.observeUnclaimed = null
        }
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
        val status = remoteAccessibility.status()
        if (Build.VERSION.SDK_INT < 33 || status == "active") {
            accessibility()
            return
        }
        TvMenus.show(
            owner,
            "버튼 맵핑 설정",
            listOf("GenieBreaker 앱 정보", "접근성 설정", "닫기"),
            if (status == "restricted")
                "Android가 이 설치본의 접근성 설정을 제한하고 있습니다. 앱 정보의 더보기에서 ‘제한된 설정 허용’을 선택한 뒤, 접근성에서 ‘GenieBreaker 버튼 맵핑’을 다시 켜세요."
            else
                "접근성에서 ‘GenieBreaker 버튼 맵핑’을 켜세요. 업데이트 후 스위치가 다시 꺼지면 앱 정보의 더보기에서 ‘제한된 설정 허용’을 확인하세요."
        ) { index ->
            when (index) {
                0 -> {
                    if (!openAppDetails(owner, owner.packageName)) TvMenus.notice(owner, "TV 설정의 앱 목록에서 GenieBreaker를 선택하세요.")
                }
                1 -> accessibility()
            }
        }
    }
    fun chooseButton() {
        TvMenus.show(
            owner, "버튼 추가", listOf("취소"),
            "$CAPTURE_INSTRUCTIONS\n\n특수 버튼 입력 대기 중"
        )
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
