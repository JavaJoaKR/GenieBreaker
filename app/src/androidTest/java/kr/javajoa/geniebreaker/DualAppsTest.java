package kr.javajoa.geniebreaker;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class DualAppsTest {
    @Test(timeout=120000) public void createsIdentityAndReusesKeyWithoutUserInput() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DualApps manager = new DualApps(context);
        DualApp first = null, second = null;
        try {
            android.content.pm.PackageInfo source = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            first = manager.build(source, progress -> kotlin.Unit.INSTANCE);
            second = manager.build(source, progress -> kotlin.Unit.INSTANCE);
            assertNotEquals(source.packageName, first.getTarget());
            assertEquals(first.getTarget(), second.getTarget());
            assertEquals(first.getCertificate(), second.getCertificate());
            assertTrue(first.getLabel().endsWith(" 듀얼"));
            assertEquals(second.getId(), manager.record(source.packageName).getId());
            assertEquals(first.getTarget(), context.getPackageManager().getPackageArchiveInfo(manager.file(first).getAbsolutePath(), 0).packageName);
        } finally {
            for (DualApp record : new DualApp[]{first, second}) if (record != null) {
                manager.file(record).delete();
                new java.io.File(manager.file(record).getParentFile(), record.getId() + ".json").delete();
            }
            context.getSharedPreferences("dual_apps", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }
}
