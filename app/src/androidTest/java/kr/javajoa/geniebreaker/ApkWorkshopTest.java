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
public class ApkWorkshopTest {
    private String shell(String command) throws Exception {
        ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
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
            PackageInfo parsed = context.getPackageManager().getPackageArchiveInfo(result.getFile().getAbsolutePath(), 0);
            assertNotNull(parsed); assertEquals(name, parsed.packageName); assertEquals(42, parsed.versionCode);
            assertTrue(result.getCertificate().length() > 50);
            shell("run-as " + context.getPackageName() + " cat " + result.getFile().getAbsolutePath() + " > /data/local/tmp/genie-edit-test.apk");
            String install = shell("pm install -t /data/local/tmp/genie-edit-test.apk");
            assertTrue("Edited APK installation failed: " + install, install.contains("Success"));
            assertNotNull(context.getPackageManager().getPackageInfo(name, 0));
        } finally {
            shell("pm uninstall " + name); shell("rm -f /data/local/tmp/genie-edit-test.apk");
            if (result != null) {
                result.getFile().delete();
                new java.io.File(result.getFile().getParentFile(), result.getId() + ".json").delete();
            }
        }
    }
}
