package kr.javajoa.geniebreaker.remoteprobe;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Locale;

/** Runs without Flutter, storage, accessibility, or network permissions. */
public final class ProbeActivity extends Activity {
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private TextView state;
    private TextView events;
    private int focused;
    private int clicked;
    private int count;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(22), dp(28), dp(22));
        root.setBackgroundColor(0xff121f2f);

        root.addView(label("GenieBreaker 리모컨 진단", 27));
        root.addView(label("아래 → 오른쪽 → 위 → 왼쪽 → 확인 순서로 한 번씩 눌러 주세요.", 17));
        state = label("", 18);
        root.addView(state);

        Button[] buttons = new Button[4];
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            root.addView(line);
            for (int column = 0; column < 2; column++) {
                int number = row * 2 + column + 1;
                Button button = new Button(this);
                buttons[number - 1] = button;
                button.setId(View.generateViewId());
                button.setText("선택 " + number);
                button.setTextSize(18);
                button.setFocusableInTouchMode(true);
                button.setBackgroundTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}},
                    new int[]{0xff3d6795, 0xff203044}
                ));
                button.setOnFocusChangeListener((view, selected) -> {
                    if (selected) {
                        focused = number;
                        render();
                    }
                });
                button.setOnClickListener(view -> {
                    clicked = number;
                    render();
                });
                line.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
            }
        }
        events = label("", 14);
        events.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(events);
        setContentView(root);
        buttons[0].requestFocus();
        render();
    }

    @Override public void onWindowFocusChanged(boolean focusedWindow) {
        super.onWindowFocusChanged(focusedWindow);
        render();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        remember(String.format(Locale.ROOT, "%s %s scan=%d source=%x meta=%x repeat=%d",
            event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" :
                event.getAction() == KeyEvent.ACTION_UP ? "UP" : "MULTI",
            KeyEvent.keyCodeToString(event.getKeyCode()), event.getScanCode(), event.getSource(),
            event.getMetaState(), event.getRepeatCount()));
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_DPAD) || event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            remember(String.format(Locale.ROOT, "MOTION source=%x hat=(%.1f,%.1f) scroll=(%.1f,%.1f)",
                event.getSource(), event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y), event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL)));
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private void remember(String event) {
        if (recent.size() == 10) recent.removeFirst();
        recent.addLast(event);
        count++;
        render();
    }

    private void render() {
        if (state != null) state.setText((hasWindowFocus() ? "창 활성" : "창 비활성") +
            "   ·   포커스 " + focused + "   ·   확인 " + clicked + "   ·   이벤트 " + count);
        if (events != null) events.setText(recent.isEmpty() ? "받은 입력 없음" : String.join("\n", recent));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }
}
