package kr.javajoa.tvinstallcheck;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class InstallCheckActivity extends Activity {
    private InstallProbe probe;
    private TextView installed;
    private TextView result;
    private Button run;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (state, key) -> refresh();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        probe = new InstallProbe(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff151515);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int margin = Math.round(32 * getResources().getDisplayMetrics().density);
        panel.setPadding(margin, margin, margin, margin);
        scroll.addView(panel);

        panel.addView(label("GenieBreaker 설치 진단", 28));
        installed = label("", 18);
        panel.addView(installed);
        run = new Button(this);
        run.setText("공식 0.1.170 설치 확인");
        run.setOnClickListener(view -> check());
        panel.addView(run);
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

    private void check() {
        String conflict = probe.conflict();
        if (conflict != null) {
            showResult(conflict);
            return;
        }
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
        probe.begin();
    }

    private void showResult(String message) {
        probe.state.edit().putString("result", message).apply();
    }

    private void refresh() {
        installed.setText(probe.installedSummary());
        run.setEnabled(!probe.busy());
        result.setText(probe.state.getString("result", "설치 버튼을 누르면 공식 APK를 내려받고 Android 확인 화면을 엽니다."));
    }

    private TextView label(String value, int size) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(Color.WHITE);
        return label;
    }
}
