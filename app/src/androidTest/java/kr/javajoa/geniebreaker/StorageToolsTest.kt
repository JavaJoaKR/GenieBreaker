package kr.javajoa.geniebreaker

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageToolsTest {
    @Test fun cleanupPreservesRegistryAndOtherAppData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = context.getSharedPreferences("dual_apps", 0)
        registry.edit().putString("cleanup_probe", "preserve").commit()
        val kept = File(context.filesDir, "cleanup-probe.txt").apply { writeText("keep") }
        val apk = File(context.filesDir, "edited/cleanup-probe.apk").apply { parentFile!!.mkdirs(); writeText("apk") }
        val cache = File(context.cacheDir, "cleanup-probe.tmp").apply { writeText("temporary") }
        try {
            StorageTools.clean(context, "apks")
            assertFalse(apk.exists()); assertTrue(cache.exists())
            StorageTools.clean(context, "cache")
            assertFalse(cache.exists()); assertTrue(kept.exists())
            assertEquals("preserve", registry.getString("cleanup_probe", null))
        } finally { kept.delete(); registry.edit().remove("cleanup_probe").commit() }
    }
    @Test fun cleanupRefusesActiveFileWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FileWork.reading {
            try { StorageTools.clean(context, "cache"); fail("Cleanup must not overlap a file read") }
            catch (_: IllegalStateException) { }
        }
    }
}
