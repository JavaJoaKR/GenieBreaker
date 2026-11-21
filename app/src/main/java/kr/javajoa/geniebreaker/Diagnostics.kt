package kr.javajoa.geniebreaker

import android.content.Context
import java.io.File

internal enum class DiagnosticStep(val label: String) {
    BLUETOOTH_PAIR("페어링"),
    BLUETOOTH_CONNECT("연결"),
    BLUETOOTH_DISCONNECT("연결 해제"),
    BLUETOOTH_FORGET("등록 삭제"),
    BLUETOOTH_PROFILE("프로필 준비"),
    BLUETOOTH_SCAN("기기 검색"),
    BLUETOOTH_SCAN_STOP("기기 검색 중지"),
    BLUETOOTH_ENABLE("블루투스 켜기"),
    BLUETOOTH_COMPANION("보조 페어링"),
    ACCESSIBILITY("접근성"),
    INSTALL("APK 설치"),
    LAUNCH("앱 실행"),
    SHORTCUT("블루투스 기기 목록 열기")
}

internal enum class DiagnosticCause(val label: String) {
    REASON_UNAVAILABLE("시스템이 요청을 거절했습니다. 상세 사유가 제공되지 않았습니다."),
    ERROR_UNSPECIFIED("시스템 요청 중 오류가 발생했습니다. 상세 사유를 확인할 수 없습니다."),
    SYSTEM_PERMISSION("Android가 권한 검사를 통과시키지 않았습니다."),
    PRIVILEGED_BLUETOOTH("Android가 시스템 앱 전용 블루투스 권한을 요구합니다."),
    DEVICE_POLICY("현재 사용자의 기기 정책이 블루투스 사용을 금지합니다."),
    BLUETOOTH_SETTINGS_RESTRICTED("현재 사용자의 기기 정책이 시스템 블루투스 설정 변경을 제한합니다."),
    DISCOVERY_STILL_ACTIVE("기기 검색이 아직 끝나지 않았습니다. 검색이 끝난 뒤 페어링을 다시 시도하세요."),
    SCAN_PERMISSION("앱에 블루투스 검색 권한이 없습니다."),
    CONNECT_PERMISSION("앱에 블루투스 연결 권한이 없습니다."),
    LOCATION_PERMISSION("이 Android 버전에서 기기 검색에 필요한 위치 권한이 없습니다."),
    SCAN_APP_OP("Android가 앱의 블루투스 검색 작업을 차단했습니다."),
    CONNECT_APP_OP("Android가 앱의 블루투스 연결 작업을 차단했습니다."),
    LOCATION_DISABLED("TV의 위치 서비스가 꺼져 있습니다."),
    BLUETOOTH_DISABLED("TV의 블루투스가 꺼져 있습니다."),
    BLUETOOTH_UNAVAILABLE("TV가 블루투스 어댑터를 제공하지 않습니다."),
    PROFILE_REJECTED("Android에서 연결 프로필 요청을 받아들이지 않았습니다. 상세 사유가 제공되지 않았습니다."),
    PAIRING_ENDED("Android가 페어링을 완료하기 전에 종료했습니다. 상세 사유가 제공되지 않았습니다."),
    SYSTEM_FEATURE_UNAVAILABLE("Android에서 이 제어 기능을 앱에 공개하지 않았습니다."),
    SCREEN_UNAVAILABLE("이 TV는 앱에서 열 수 있는 설정 화면을 제공하지 않습니다."),
    ACCESSIBILITY_RESTRICTED("Android의 제한된 설정 정책이 이 앱의 접근성 설정을 막았습니다."),
    INSTALL_PERMISSION("이 앱의 ‘알 수 없는 앱 설치’가 허용되지 않았습니다."),
    INSTALL_BLOCKED("Android 패키지 설치 프로그램이 차단했습니다. 상세 사유가 제공되지 않았습니다."),
    INSTALL_DEVICE_POLICY("현재 사용자에게 적용된 기기 정책이 앱 설치를 금지합니다."),
    INSTALL_CONFLICT("Android가 기존 설치와의 충돌을 확인했습니다."),
    INSTALL_INCOMPATIBLE("APK가 TV의 설치 요건을 충족하지 못했습니다."),
    INSTALL_STORAGE("설치에 필요한 저장 공간이 부족합니다."),
    LAUNCH_TARGET_MISSING("시스템이 열 수 있는 앱 실행 화면을 찾지 못했습니다.")
}

/** Local identifiers only: no device metadata, identifiers, paths, raw messages or uploads. */
internal object Diagnostics {
    private var installed = false
    @Synchronized fun record(context: Context, operation: String, error: Throwable? = null,
                             step: DiagnosticStep? = null, phase: DiagnosticStep? = null,
                             cause: DiagnosticCause? = null): String {
        val code = ErrorCodes.create(operation, error?.javaClass?.simpleName)
        try {
            val file = File(context.filesDir, "diagnostics.txt")
            if (file.length() > 64 * 1024) file.writeText(file.readText().takeLast(24 * 1024))
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            // Never persist caller-provided messages: they may contain identifiers or paths.
            val trace = error?.stackTrace?.filter { it.className.startsWith("kr.javajoa.geniebreaker.") }
                ?.take(8)?.joinToString("\n") { "  ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: ""
            val stages = listOfNotNull(step?.label, phase?.label).joinToString(" / ")
            val detail = if (stages.isEmpty()) "" else " · $stages"
            val explanation = cause?.let { " — ${it.label}" }.orEmpty()
            file.appendText("$time [$code] ${ErrorCodes.category(operation)} ${ErrorCodes.reason(operation, error?.javaClass?.simpleName)}$detail$explanation\n$trace\n")
        } catch (_: Exception) { }
        return code
    }
    @Synchronized fun read(context: Context): String = try {
        File(context.filesDir, "diagnostics.txt").takeIf { it.exists() }?.readText()?.takeLast(20000) ?: "저장된 오류 기록이 없습니다."
    } catch (_: Exception) { "저장된 오류 기록을 열 수 없습니다. [GB-FS-0002]" }
    fun recentCauses(context: Context): List<String> = read(context).lineSequence()
        .filter { it.getOrNull(2) == ':' && " [GB-" in it && " — " in it }
        .mapNotNull { line ->
            val action = line.substringAfter(" · ", "").substringBefore(" — ")
            val code = line.substringAfter('[', "").substringBefore(']')
            if (action.isEmpty() || code.isEmpty()) null else {
                "$action · ${line.substringAfter(" — ")} [$code]"
            }
        }
        .toList().asReversed().distinct().take(12)

    @Synchronized fun clear(context: Context) { File(context.filesDir, "diagnostics.txt").delete() }
    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        // Old logs may contain raw stack messages; discard them on the first codes-only run.
        val prefs = context.getSharedPreferences("local_diagnostics", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("codes_only", false)) { clear(context); prefs.edit().putBoolean("codes_only", true).apply() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val code = record(context, "Crash", error)
            context.getSharedPreferences("local_diagnostics", Context.MODE_PRIVATE).edit().putString("last_crash", code).commit()
            previous?.uncaughtException(thread, error)
        }
    }
}
