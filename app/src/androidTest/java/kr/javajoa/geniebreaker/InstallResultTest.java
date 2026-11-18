package kr.javajoa.geniebreaker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InstallResultTest {
    @Test public void preservesExactConflictStatusAndOwner() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences state = context.getSharedPreferences("install_results", Context.MODE_PRIVATE);
        try {
            Intent result = new Intent("kr.javajoa.geniebreaker.INSTALL_RESULT")
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_CONFLICT)
                .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_DUPLICATE_PERMISSION: global.permission")
                .putExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME, "vendor.original")
                .putExtra("expected_package", "vendor.clone");
            new InstallResultReceiver().onReceive(context, result);
            String saved = state.getString("result", "");
            assertTrue(saved.contains("INSTALL_FAILED_DUPLICATE_PERMISSION"));
            assertTrue(saved.contains("vendor.original"));
            assertTrue(saved.contains("vendor.clone"));
            assertEquals(saved, SessionInstaller.INSTANCE.resultFor(context, "vendor.clone"));
            assertNull(SessionInstaller.INSTANCE.resultFor(context, "vendor.original"));
            assertFalse(state.contains("progress"));

            Intent otherInstall = new Intent(result)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
                .putExtra("expected_package", "vendor.other");
            new InstallResultReceiver().onReceive(context, otherInstall);
            assertTrue(state.getString("result", "").contains("대상: vendor.other"));
            assertEquals(saved, SessionInstaller.INSTANCE.resultFor(context, "vendor.clone"));
        } finally { state.edit().clear().commit(); }
    }
}
