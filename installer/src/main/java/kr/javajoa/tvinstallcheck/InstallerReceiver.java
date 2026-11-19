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

        probe.complete(attempt, InstallReport.outcome(intent, status));
    }
}
