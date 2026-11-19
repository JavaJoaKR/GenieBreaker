package kr.javajoa.tvinstallcheck;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.List;

final class InstallReport {
    // The package manager includes this Android result code on supported builds.
    private static final String LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS";

    static String environment(Context context) {
        String device = "기기 · " + Build.MANUFACTURER + " " + Build.MODEL
            + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        UserManager users = context.getSystemService(UserManager.class);
        List<String> restrictions = new ArrayList<>();
        if (users != null) {
            if (users.hasUserRestriction(UserManager.DISALLOW_INSTALL_APPS)) restrictions.add("모든 앱 설치");
            if (users.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)) restrictions.add("외부 출처 앱 설치");
            if (Build.VERSION.SDK_INT >= 29
                && users.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)) {
                restrictions.add("전체 사용자 외부 출처 앱 설치");
            }
        }
        return device + "\nInstaller의 알 수 없는 앱 설치 · "
            + (context.getPackageManager().canRequestPackageInstalls() ? "허용" : "미허용")
            + "\nAndroid 사용자 제한 · "
            + (users == null ? "확인 불가" : restrictions.isEmpty() ? "해당 없음" : String.join(", ", restrictions));
    }

    static String outcome(Intent data, int status) {
        String code = data.hasExtra(LEGACY_STATUS) ? legacyCode(data.getIntExtra(LEGACY_STATUS, 0)) : "미제공";
        String message = data.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String other = data.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME);
        String result = summary(status, data.getIntExtra(LEGACY_STATUS, 0))
            + "\nAndroid 상태 · " + status + " / " + code;
        if (message != null && !message.isEmpty()) result += "\n시스템 메시지 · " + message;
        if (other != null && !other.isEmpty()) result += "\nAndroid가 지목한 관련 앱 · " + other;
        else if (status != PackageInstaller.STATUS_SUCCESS) result += "\nAndroid가 제공한 관련 앱 · 없음";
        return result;
    }

    private static String legacyCode(int code) {
        switch (code) {
            case -21: return "-21 INSTALL_FAILED_VERIFICATION_TIMEOUT";
            case -22: return "-22 INSTALL_FAILED_VERIFICATION_FAILURE";
            case -111: return "-111 INSTALL_FAILED_USER_RESTRICTED";
            default: return Integer.toString(code);
        }
    }

    private static String summary(int status, int legacy) {
        if (legacy == -21) return "Android의 APK 검증 시간이 초과됐습니다.";
        if (legacy == -22) return "Android의 APK 검증에서 설치를 거부했습니다.";
        if (legacy == -111) return "Android 사용자 제한으로 설치가 거부됐습니다.";
        switch (status) {
            case PackageInstaller.STATUS_SUCCESS: return "설치 완료";
            case PackageInstaller.STATUS_FAILURE_CONFLICT: return "설치된 패키지와 충돌했습니다.";
            case PackageInstaller.STATUS_FAILURE_BLOCKED: return "Android가 시스템 정책에 따라 설치를 차단했습니다.";
            case PackageInstaller.STATUS_FAILURE_ABORTED: return "설치가 중단됐습니다.";
            case PackageInstaller.STATUS_FAILURE_STORAGE: return "설치에 필요한 저장 공간이 부족합니다.";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: return "APK가 이 TV와 호환되지 않습니다.";
            case PackageInstaller.STATUS_FAILURE_INVALID: return "Android가 APK를 유효하지 않은 파일로 판단했습니다.";
            case PackageInstaller.STATUS_FAILURE_TIMEOUT: return "Android의 설치 처리 시간이 초과됐습니다.";
            default: return "설치를 완료하지 못했습니다.";
        }
    }
}
