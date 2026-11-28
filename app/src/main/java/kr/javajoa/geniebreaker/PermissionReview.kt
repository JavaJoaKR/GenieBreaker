package kr.javajoa.geniebreaker

import android.Manifest.permission
import android.app.Activity
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

private val reviewedPermissions = linkedMapOf(
    permission.ACCESS_FINE_LOCATION to "정확한 위치",
    permission.ACCESS_COARSE_LOCATION to "대략적인 위치",
    permission.ACCESS_BACKGROUND_LOCATION to "백그라운드 위치",
    permission.READ_PHONE_STATE to "전화 상태",
    permission.READ_BASIC_PHONE_STATE to "기본 전화 상태",
    permission.READ_PHONE_NUMBERS to "전화번호",
    permission.BLUETOOTH_SCAN to "주변 블루투스 기기 검색",
    permission.BLUETOOTH_CONNECT to "블루투스 기기 연결",
    permission.NEARBY_WIFI_DEVICES to "주변 Wi-Fi 기기",
    permission.RECORD_AUDIO to "마이크",
    permission.CAMERA to "카메라",
    permission.READ_CONTACTS to "연락처",
    permission.READ_MEDIA_IMAGES to "사진",
    permission.READ_MEDIA_VIDEO to "동영상",
    permission.READ_MEDIA_AUDIO to "오디오",
    permission.READ_MEDIA_VISUAL_USER_SELECTED to "선택한 사진 및 동영상",
    permission.READ_EXTERNAL_STORAGE to "공유 저장소 읽기",
    permission.WRITE_EXTERNAL_STORAGE to "공유 저장소 쓰기"
)

private data class PermissionReviewEntry(
    val name: String,
    val packageName: String,
    val granted: List<String>
)

@Suppress("DEPRECATION")
internal fun showPermissionReview(owner: Activity) {
    val manager = owner.packageManager
    val entries = manager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        .mapNotNull { pkg ->
            val flags = pkg.requestedPermissionsFlags
            val granted = pkg.requestedPermissions.orEmpty().mapIndexedNotNull { index, requested ->
                val state = flags?.getOrNull(index) ?: 0
                reviewedPermissions[requested]?.takeIf { state and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
            }
            if (granted.isEmpty()) return@mapNotNull null
            val label = pkg.applicationInfo?.loadLabel(manager)?.toString() ?: pkg.packageName
            PermissionReviewEntry(label, pkg.packageName, granted)
        }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.packageName }))

    val description = "허용 상태는 Android가 보고한 값입니다. 실제 접근이나 전송 기록은 표시되지 않습니다."
    val summary = if (entries.isEmpty()) "검사 대상 권한이 허용된 앱이 없습니다." else "권한이 허용된 앱 ${entries.size}개"
    TvMenus.show(owner, "앱 권한 검사",
        entries.map { "${it.name}\n${it.packageName} · 권한 ${it.granted.size}개" } + "닫기",
        "$summary\n$description") { selected ->
        val entry = entries.getOrNull(selected) ?: return@show
        val details = entry.packageName + "\n\n" + entry.granted.joinToString("\n")
        TvMenus.show(owner, entry.name, listOf("Android 앱 설정 열기", "닫기"), details) { choice ->
            if (choice == 0 && !openAppDetails(owner, entry.packageName)) {
                TvMenus.notice(owner, "이 TV에서 앱 설정 화면을 열 수 없습니다.")
            }
        }
    }
}
