package kr.javajoa.tvinstallcheck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

public final class InstallerReceiver extends BroadcastReceiver {
    static final String ACTION = "kr.javajoa.tvinstallcheck.RESULT";

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        OfficialInstaller probe = new OfficialInstaller(context);
        String attempt = intent.getStringExtra("attempt");
        if (!probe.isCurrent(attempt)) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                : intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation == null) {
                probe.complete(attempt, "Android에서 설치 확인 화면을 받지 못했습니다.");
                return;
            }
            try {
                context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException error) {
                probe.complete(attempt, "설치 확인 화면 열기 실패: " + error.getClass().getSimpleName());
            }
            return;
        }

        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String other = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME);
        int legacy = intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS", 0);
        probe.complete(attempt, statusLabel(status) + "\nAndroid 상태 · " + status + " / " + legacy
            + (message == null ? "" : "\n" + message)
            + (other == null ? "" : "\n관련 앱 · " + other));
    }

    private String statusLabel(int status) {
        switch (status) {
            case PackageInstaller.STATUS_SUCCESS: return "설치 완료";
            case PackageInstaller.STATUS_FAILURE_CONFLICT: return "패키지 충돌";
            case PackageInstaller.STATUS_FAILURE_BLOCKED: return "TV가 설치를 차단했습니다.";
            case PackageInstaller.STATUS_FAILURE_ABORTED: return "설치 취소";
            case PackageInstaller.STATUS_FAILURE_STORAGE: return "저장 공간 부족";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: return "기기 호환성 문제";
            case PackageInstaller.STATUS_FAILURE_INVALID: return "유효하지 않은 APK";
            default: return "설치 실패";
        }
    }
}
