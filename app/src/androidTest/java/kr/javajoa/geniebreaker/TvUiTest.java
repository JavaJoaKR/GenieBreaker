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
    @Test public void testLateNameKeepsFocusAndCaptureScreen() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(intent);
        try {
            instrumentation.waitForIdleSync();
            instrumentation.setInTouchMode(false);
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            instrumentation.waitForIdleSync();
            final boolean[] preserved = {false};
            instrumentation.runOnMainSync(() -> preserved[0] = activity.previewLateName());
            instrumentation.waitForIdleSync();
            assertTrue("Late name must retain row identity and D-pad focus", preserved[0]);
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try (android.os.ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/genie-tv-ui.png");
                 java.io.InputStream pipe = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                while (pipe.read() != -1) { }
            }
            screenshot.recycle();
            instrumentation.runOnMainSync(activity::previewAppsTab);
            android.os.SystemClock.sleep(800);
            instrumentation.runOnMainSync(() -> {
                android.widget.GridView shelf = findGrid(activity.findViewById(android.R.id.content));
                assertNotNull("App shelf must exist", shelf);
                assertNotNull("App shelf must attach its adapter on first load", shelf.getAdapter());
                assertTrue("Launchable apps must be displayed", shelf.getAdapter().getCount() > 0);
            });
            instrumentation.waitForIdleSync();
            try (android.os.ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/genie-app-ui.png");
                 java.io.InputStream pipe = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { while (pipe.read() != -1) { } }

            final int[] choice = {-1};
            instrumentation.runOnMainSync(() -> TvMenus.INSTANCE.show(activity, "보기 설정", java.util.Arrays.asList("실행 가능한 앱", "전체 설치 앱"), "", 0, index -> { choice[0] = index; return kotlin.Unit.INSTANCE; }));
            android.os.SystemClock.sleep(4000);
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            android.os.SystemClock.sleep(300);
            capture(instrumentation, "genie-flutter-menu.png");
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            for (int attempt = 0; attempt < 30 && choice[0] < 0; attempt++) android.os.SystemClock.sleep(200);
            assertEquals("Flutter menu returns the remote-selected choice", 1, choice[0]);
            android.os.SystemClock.sleep(300);
            java.util.Map<String, Boolean> options = new java.util.HashMap<>();
            for (String key : new String[]{"renameApp", "renameLaunchers", "renameResources", "isolateTasks", "extractLibraries"}) options.put(key, true);
            instrumentation.runOnMainSync(() -> TvMenus.INSTANCE.cloneOptions(activity, "샘플 TV 앱", options, values -> kotlin.Unit.INSTANCE));
            android.os.SystemClock.sleep(3000);
            capture(instrumentation, "genie-clone-options.png");
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            android.os.SystemClock.sleep(300);
            instrumentation.runOnMainSync(activity::previewFilesTab);
            android.os.SystemClock.sleep(3500);
            capture(instrumentation, "genie-files-ui.png");
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            android.os.SystemClock.sleep(500);
            instrumentation.runOnMainSync(() -> assertTrue("Left key returns from Flutter files to native sidebar", activity.getCurrentFocus() instanceof android.widget.Button && ((android.widget.Button) activity.getCurrentFocus()).getText().toString().equals("파일탐색기")));

        } finally { instrumentation.runOnMainSync(activity::finish); }
    }
    private android.widget.GridView findGrid(android.view.View view) {
        if (view instanceof android.widget.GridView) return (android.widget.GridView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.GridView grid = findGrid(group.getChildAt(i)); if (grid != null) return grid;
            }
        }
        return null;
    }
    private void capture(Instrumentation instrumentation, String name) throws Exception {
        try (android.os.ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/" + name);
             java.io.InputStream pipe = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { while (pipe.read() != -1) { } }
    }
}
