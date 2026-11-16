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
        for (String path : new String[]{"/secrets", "/../update.apk", "/updates/update.apk"}) {
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
            File output = new File(activity.getExternalFilesDir(null), "tv-ui.png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            screenshot.recycle();
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }
}
