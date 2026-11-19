package kr.javajoa.tvinstallcheck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class InstallerActivity extends Activity {
    private OfficialInstaller probe;
    private TextView installed;
    private TextView result;
    private Button run;
    private Button diagnose;
    private Button copy;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (state, key) -> refresh();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        probe = new OfficialInstaller(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff151515);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int margin = Math.round(32 * getResources().getDisplayMetrics().density);
        panel.setPadding(margin, margin, margin, margin);
        scroll.addView(panel);

        panel.addView(label("GenieBreaker 설치 프로그램", 28));
        installed = label("", 18);
        panel.addView(installed);
        run = new Button(this);
        run.setText("최신 버전 설치");
        run.setOnClickListener(view -> check(false));
        panel.addView(run);
        diagnose = new Button(this);
        diagnose.setText("설치 진단");
        diagnose.setOnClickListener(view -> new AlertDialog.Builder(this)
            .setTitle("설치 진단")
            .setMessage("Android에 최신 APK 설치를 요청합니다. 이미 최신 버전이어도 설치 확인 화면이 열립니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("진단 시작", (dialog, which) -> check(true))
            .show());
        panel.addView(diagnose);
        copy = new Button(this);
        copy.setText("설치 결과 복사");
        copy.setOnClickListener(view -> copyResult());
        panel.addView(copy);
        result = label("", 18);
        panel.addView(result);
        setContentView(scroll);
        run.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        probe.state.registerOnSharedPreferenceChangeListener(listener);
        refresh();
    }

    @Override
    protected void onStop() {
        probe.state.unregisterOnSharedPreferenceChangeListener(listener);
        super.onStop();
    }

    private void check(boolean retryLatest) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            showResult("이 앱에 ‘알 수 없는 앱 설치’를 허용한 뒤 돌아와서 다시 선택하세요.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.fromParts("package", getPackageName(), null)));
            } catch (ActivityNotFoundException | SecurityException error) {
                showResult("TV에서 이 앱의 설치 권한 화면을 열 수 없습니다: " + error.getClass().getSimpleName());
            }
            return;
        }
        probe.begin(retryLatest);
    }

    private void showResult(String message) {
        probe.state.edit().putString("result", message + "\n\n" + InstallReport.environment(this)).apply();
    }

    private void refresh() {
        installed.setText(probe.installedSummary() + "\n" + probe.installedSource());
        boolean ready = !probe.busy();
        run.setEnabled(ready);
        diagnose.setEnabled(ready);
        String report = probe.state.getString("result", null);
        copy.setEnabled(ready && report != null);
        result.setText(report == null ? "최신 GenieBreaker를 내려받고 Android 설치 확인 화면을 엽니다." : report);
    }

    private void copyResult() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("GenieBreaker 설치 결과", result.getText()));
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "설치 결과를 복사했습니다.", Toast.LENGTH_SHORT).show();
    }

    private TextView label(String value, int size) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(Color.WHITE);
        return label;
    }
}
