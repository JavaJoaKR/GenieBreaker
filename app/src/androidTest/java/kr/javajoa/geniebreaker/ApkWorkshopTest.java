package kr.javajoa.geniebreaker;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 34)
public class ApkWorkshopTest {
    private String shell(String command) throws Exception {
        ParcelFileDescriptor[] descriptors = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommandRwe(command);
        descriptors[1].close();
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]);
             InputStream errors = new ParcelFileDescriptor.AutoCloseInputStream(descriptors[2]);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            while ((n = errors.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }
    @Test(timeout=120000) public void editsSignsAndInstallsSeparatePackage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = context.getPackageName() + ".editprobe";
        shell("pm uninstall " + name);
        EditedApk result = null;
        try {
            ApkEditSpec spec = new ApkEditSpec("APK edit verification", name, 42, false);
            result = new ApkWorkshop(context).build(context.getApplicationInfo(), spec, progress -> kotlin.Unit.INSTANCE);
            PackageInfo parsed = context.getPackageManager().getPackageArchiveInfo(result.getFile().getAbsolutePath(), android.content.pm.PackageManager.GET_PERMISSIONS | android.content.pm.PackageManager.GET_PROVIDERS | android.content.pm.PackageManager.GET_ACTIVITIES);
            assertNotNull(parsed); assertEquals(name, parsed.packageName); assertEquals(42, parsed.versionCode);
            assertEquals("APK edit verification", parsed.applicationInfo.nonLocalizedLabel.toString());
            assertTrue(result.getCertificate().length() > 50);
            parsed.applicationInfo.sourceDir = result.getFile().getAbsolutePath();
            parsed.applicationInfo.publicSourceDir = result.getFile().getAbsolutePath();
            for (android.content.pm.ActivityInfo activity : parsed.activities) {
                if (activity.name.contains("StartupProbe")) assertEquals("APK edit verification", activity.nonLocalizedLabel.toString());
            }
            assertNotNull(parsed.permissions);
            for (android.content.pm.PermissionInfo permission : parsed.permissions) {
                assertTrue("Declared permission must belong to clone namespace: " + permission.name, permission.name.startsWith(name + "."));
            }
            assertNotNull(parsed.providers);
            for (android.content.pm.ProviderInfo provider : parsed.providers) {
                assertFalse("Original global provider authority survived", provider.authority.contains("org.geniebreaker.fixture.provider"));
            }

            ParcelFileDescriptor[] copy = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommandRwe("dd of=/data/local/tmp/genie-edit-test.apk bs=8192");
            try (java.io.InputStream input = new java.io.FileInputStream(result.getFile());
                 java.io.OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(copy[1])) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            }
            try (InputStream stdout = new ParcelFileDescriptor.AutoCloseInputStream(copy[0]);
                 InputStream stderr = new ParcelFileDescriptor.AutoCloseInputStream(copy[2])) {
                while (stdout.read() != -1) { }
                while (stderr.read() != -1) { }
            }
            assertTrue("Test APK must be copied for shell installer", shell("ls -l /data/local/tmp/genie-edit-test.apk").contains(Long.toString(result.getFile().length())));
            String install = shell("pm install -t /data/local/tmp/genie-edit-test.apk");
            assertTrue("Edited APK installation failed: " + install, install.contains("Success"));
            assertNotNull(context.getPackageManager().getPackageInfo(name, 0));
            String start = shell("am start -W -n " + name + "/" + context.getPackageName() + ".StartupProbeAlias");
            assertTrue("Cloned alias did not start: " + start, start.contains("Status: ok"));
            boolean ready = false;
            for (int attempt = 0; attempt < 20; attempt++) {
                android.view.accessibility.AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    ready = !root.findAccessibilityNodeInfosByText("CLONE_PROBE_READY").isEmpty(); root.recycle();
                    if (ready) break;
                }
                android.os.SystemClock.sleep(250);
            }
            assertTrue("Cloned activity failed after installation. " + shell("logcat -d -b crash -t 30"), ready);
            assertFalse("Cloned process exited after startup", shell("pidof " + name).trim().isEmpty());
        } finally {
            shell("pm uninstall " + name); shell("rm -f /data/local/tmp/genie-edit-test.apk");
            if (result != null) {
                result.getFile().delete();
                new java.io.File(result.getFile().getParentFile(), result.getId() + ".json").delete();
            }
        }
    }
}
