package kr.javajoa.tvdeviceinfo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Reads public device properties locally without any Android permissions. */
public final class DeviceInfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff151515);
        scroll.setFocusable(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(24), dp(32), dp(24));
        scroll.addView(content);

        TextView title = text(getString(R.string.app_name), 28, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(18));
        content.addView(title);

        addRow(content, R.string.android_version, Build.VERSION.RELEASE);
        addRow(content, R.string.sdk_version, String.valueOf(Build.VERSION.SDK_INT));
        addRow(content, R.string.model, Build.MANUFACTURER + " · " + Build.MODEL);
        addRow(content, R.string.device, Build.DEVICE);
        addRow(content, R.string.abis, supportedAbis());
        addRow(content, R.string.build, Build.DISPLAY);
        addRow(content, R.string.available_storage, Formatter.formatFileSize(this, getFilesDir().getUsableSpace()));

        setContentView(scroll);
        scroll.requestFocus();
    }

    @SuppressWarnings("deprecation")
    private String supportedAbis() {
        if (Build.VERSION.SDK_INT >= 21) {
            return TextUtils.join(", ", Build.SUPPORTED_ABIS);
        }
        return TextUtils.isEmpty(Build.CPU_ABI2) || Build.CPU_ABI.equals(Build.CPU_ABI2)
            ? Build.CPU_ABI : Build.CPU_ABI + ", " + Build.CPU_ABI2;
    }

    private void addRow(LinearLayout content, int label, String value) {
        TextView row = text(
            getString(label) + "   " + (TextUtils.isEmpty(value) ? getString(R.string.unknown) : value),
            18, 0xfff0f0f0
        );
        row.setPadding(0, dp(5), 0, dp(5));
        content.addView(row);
    }

    private TextView text(String value, int size, int color) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        return result;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
