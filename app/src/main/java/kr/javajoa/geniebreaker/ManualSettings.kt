package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

internal const val PREVIOUSLY_CONNECTED_DEVICES = "com.android.settings.PREVIOUSLY_CONNECTED_DEVICE"
private const val SETTINGS_SHORTCUT = "com.android.settings.SHORTCUT"

internal data class SettingsLink(val label: String, val identifier: String, val intent: Intent)

private fun systemSettingsTargets(context: Context, request: Intent, defaultOnly: Boolean = true): List<SettingsLink> {
    val pm = context.packageManager
    val flags = PackageManager.MATCH_SYSTEM_ONLY or (if (defaultOnly) PackageManager.MATCH_DEFAULT_ONLY else 0)
    return pm.queryIntentActivities(request, flags).mapNotNull { match ->
        val screen = match.activityInfo
        if (!screen.exported || !screen.enabled) return@mapNotNull null
        val permission = screen.permission
        if (permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            return@mapNotNull null
        }
        val component = ComponentName(screen.packageName, screen.name)
        SettingsLink(match.loadLabel(pm).toString(), component.flattenToShortString(), Intent(request).setComponent(component))
    }.distinctBy { it.identifier }
}

internal fun openSystemSettings(context: Context, request: Intent): Boolean =
    systemSettingsTargets(context, request).any { openScreen(context, it.intent) }

internal fun declaredSettingsShortcuts(context: Context): List<SettingsLink> {
    val packages = systemSettingsTargets(context, Intent(Settings.ACTION_SETTINGS))
        .mapNotNull { it.intent.component?.packageName }.toSet()
    val request = Intent(Intent.ACTION_MAIN).addCategory(SETTINGS_SHORTCUT)
    return systemSettingsTargets(context, request, defaultOnly = false)
        .filter { it.intent.component?.packageName in packages }
}

internal fun showManualSettings(owner: Activity) {
    val routes = settingsGroups.mapValues { (_, entries) ->
        entries.mapNotNull { (label, action) ->
            val requests = if (action in packageActions) {
                listOf(Intent(action, Uri.fromParts("package", owner.packageName, null)), Intent(action))
            } else listOf(Intent(action))
            val target = requests.firstNotNullOfOrNull { systemSettingsTargets(owner, it).firstOrNull() }
            target?.copy(label = label, identifier = action)
        }
    }.filterValues { it.isNotEmpty() }.toMutableMap()
    val declared = declaredSettingsShortcuts(owner).sortedBy { it.label.lowercase() }
    if (declared.isNotEmpty()) routes["TV에서 제공한 추가 설정"] = declared
    if (routes.isEmpty()) {
        TvMenus.notice(owner, "이 TV에서 앱으로 열 수 있는 Android 설정이 없습니다.")
        return
    }
    val groups = routes.entries.toList()
    TvMenus.show(owner, "수동 바로가기", groups.map { it.key } + "닫기") { group ->
        val selected = groups.getOrNull(group) ?: return@show
        val links = selected.value
        TvMenus.show(owner, selected.key, links.map { "${it.label}\n${it.identifier}" } + "닫기") { index ->
            val link = links.getOrNull(index) ?: return@show
            if (!openScreen(owner, link.intent)) {
                TvMenus.notice(owner, "TV에서 ‘${link.label}’ 설정을 열 수 없습니다.")
            }
        }
    }
}

private val packageActions = setOf(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Settings.ACTION_APP_LOCALE_SETTINGS,
    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Settings.ACTION_MANAGE_WRITE_SETTINGS
)

private val settingsGroups = linkedMapOf(
    "연결 및 네트워크" to listOf(
        "등록된 블루투스 기기" to PREVIOUSLY_CONNECTED_DEVICES,
        "블루투스" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "네트워크 및 연결" to Settings.ACTION_WIRELESS_SETTINGS,
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
        "Wi-Fi 고급 설정" to Settings.ACTION_WIFI_IP_SETTINGS,
        "전송" to Settings.ACTION_CAST_SETTINGS,
        "VPN" to Settings.ACTION_VPN_SETTINGS,
        "데이터 사용량" to Settings.ACTION_DATA_USAGE_SETTINGS,
        "비행기 모드" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "NFC" to Settings.ACTION_NFC_SETTINGS,
        "모바일 네트워크" to Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
        "액세스 포인트 이름" to Settings.ACTION_APN_SETTINGS
    ),
    "기기 및 시스템" to listOf(
        "Android 설정" to Settings.ACTION_SETTINGS,
        "기기 정보" to Settings.ACTION_DEVICE_INFO_SETTINGS,
        "시스템 업데이트" to "android.settings.SYSTEM_UPDATE_SETTINGS",
        "내부 저장공간" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "외부 저장공간" to Settings.ACTION_MEMORY_CARD_SETTINGS,
        "날짜 및 시간" to Settings.ACTION_DATE_SETTINGS,
        "언어 및 지역" to Settings.ACTION_LOCALE_SETTINGS,
        "키보드 설정" to Settings.ACTION_INPUT_METHOD_SETTINGS,
        "물리 키보드" to Settings.ACTION_HARD_KEYBOARD_SETTINGS,
        "기본 홈 앱" to Settings.ACTION_HOME_SETTINGS,
        "개발자 옵션" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "인쇄" to Settings.ACTION_PRINT_SETTINGS,
        "배터리 절약" to Settings.ACTION_BATTERY_SAVER_SETTINGS
    ),
    "화면 및 소리" to listOf(
        "디스플레이" to Settings.ACTION_DISPLAY_SETTINGS,
        "화면 회전" to Settings.ACTION_AUTO_ROTATE_SETTINGS,
        "야간 화면" to Settings.ACTION_NIGHT_DISPLAY_SETTINGS,
        "화면 보호기" to Settings.ACTION_DREAM_SETTINGS,
        "소리" to Settings.ACTION_SOUND_SETTINGS,
        "방해 금지 우선순위" to Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
    ),
    "앱 및 알림" to listOf(
        "앱 설정" to Settings.ACTION_APPLICATION_SETTINGS,
        "앱 관리" to Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
        "설치된 모든 앱" to Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
        "기본 앱" to Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        "GenieBreaker 앱 정보" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "GenieBreaker 앱 언어" to Settings.ACTION_APP_LOCALE_SETTINGS,
        "GenieBreaker 링크 열기" to Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        "앱 알림" to Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS,
        "알림 접근" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        "방해 금지 모드 접근" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
        "배터리 최적화" to Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
        "사용 정보 접근" to Settings.ACTION_USAGE_ACCESS_SETTINGS,
        "알 수 없는 앱 설치" to Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "다른 앱 위에 표시" to Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "시스템 설정 변경" to Settings.ACTION_MANAGE_WRITE_SETTINGS
    ),
    "접근성, 계정 및 보안" to listOf(
        "접근성" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "자막" to Settings.ACTION_CAPTIONING_SETTINGS,
        "음성 입력" to Settings.ACTION_VOICE_INPUT_SETTINGS,
        "계정 동기화" to Settings.ACTION_SYNC_SETTINGS,
        "위치 서비스" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "개인정보" to Settings.ACTION_PRIVACY_SETTINGS,
        "보안" to Settings.ACTION_SECURITY_SETTINGS,
        "인증 정보 제공자" to Settings.ACTION_CREDENTIAL_PROVIDER,
        "자동 완성 서비스" to Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
        "규제 정보" to Settings.ACTION_SHOW_REGULATORY_INFO
    )
)
