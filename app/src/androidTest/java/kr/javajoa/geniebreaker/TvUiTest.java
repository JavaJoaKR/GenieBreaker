package kr.javajoa.geniebreaker;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;

/** Runs against actual Android views on a 960x540dp landscape emulator. */
@RunWith(AndroidJUnit4.class)
public class TvUiTest {
    @Test public void testUpdateProviderRejectsOtherFilesAndWrites() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "content://" + context.getPackageName() + ".updates";
        for (String path : new String[]{"/secrets", "/../update.apk", "/updates/update.apk", "/edited/../../secrets", "/edited/not-a-uuid", "/browse/not-a-token", "/browse/../../secrets"}) {
            try { context.getContentResolver().openFileDescriptor(android.net.Uri.parse(prefix + path), "r"); fail("Unexpected file was exposed"); }
            catch (java.io.FileNotFoundException expected) { }
        }
        try { context.getContentResolver().openFileDescriptor(android.net.Uri.parse(prefix + "/update.apk"), "rw"); fail("Provider allowed writes"); }
        catch (java.io.FileNotFoundException expected) { }
    }
    @Test public void captureFlutterScreensAndMenus() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
            .putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.setInTouchMode(false);
            android.os.SystemClock.sleep(2500);
            capture(instrumentation, "genie-tv-ui.png");
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            android.os.SystemClock.sleep(1500);
            capture(instrumentation, "genie-app-ui.png");
            instrumentation.runOnMainSync(() -> TvMenus.INSTANCE.show(activity, "보기 설정", java.util.Arrays.asList("실행 가능한 앱", "전체 설치 앱"), "", 0, index -> kotlin.Unit.INSTANCE));
            android.os.SystemClock.sleep(500);
            capture(instrumentation, "genie-flutter-menu.png");
            instrumentation.runOnMainSync(() -> TvMenus.INSTANCE.show(activity, "하위 메뉴", java.util.Arrays.asList("닫기"), "상위 메뉴로 돌아갈 수 있습니다.", -1, index -> kotlin.Unit.INSTANCE));
            android.os.SystemClock.sleep(250);
            capture(instrumentation, "genie-popup-depth.png");
            key(instrumentation, android.view.KeyEvent.KEYCODE_BACK);
            key(instrumentation, android.view.KeyEvent.KEYCODE_BACK);
            java.util.Map<String, Boolean> options = new java.util.HashMap<>();
            for (String key : new String[]{"renameApp", "renameLaunchers", "renameResources", "isolateTasks", "extractLibraries"}) options.put(key, true);
            instrumentation.runOnMainSync(() -> TvMenus.INSTANCE.cloneOptions(activity, "샘플 TV 앱", options, values -> kotlin.Unit.INSTANCE));
            android.os.SystemClock.sleep(500);
            capture(instrumentation, "genie-clone-options.png");
            key(instrumentation, android.view.KeyEvent.KEYCODE_BACK);
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            android.os.SystemClock.sleep(700);
            capture(instrumentation, "genie-files-ui.png");
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            android.os.SystemClock.sleep(500);
            capture(instrumentation, "genie-storage-ui.png");
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_UP);
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_UP);
            key(instrumentation, android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            android.os.SystemClock.sleep(300);
            capture(instrumentation, "genie-shortcuts-ui.png");
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }
    private void key(Instrumentation instrumentation, int code) {
        instrumentation.sendKeyDownUpSync(code);
        // Android idle does not imply that the Dart isolate rendered a channel response.
        instrumentation.waitForIdleSync();
        android.os.SystemClock.sleep(350);
    }
    private void capture(Instrumentation instrumentation, String name) throws Exception {
        try (android.os.ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/" + name);
             java.io.InputStream pipe = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { while (pipe.read() != -1) { } }
    }
}
