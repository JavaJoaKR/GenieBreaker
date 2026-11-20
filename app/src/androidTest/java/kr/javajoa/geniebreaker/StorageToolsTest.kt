package kr.javajoa.geniebreaker

import android.os.SystemClock
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
    @Test fun updateCleanupAndStartupPrunePreserveUnrelatedFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val update = File(context.cacheDir, "updates/update.apk").apply { parentFile!!.mkdirs(); writeText("old-invalid") }
        val partial = File(context.cacheDir, "updates/download-old.apk").apply { writeText("partial") }
        val imported = File(context.cacheDir, "imports/abandoned.apk").apply { parentFile!!.mkdirs(); writeText("old-copy") }
        val kept = File(context.cacheDir, "other-cache.tmp").apply { writeText("keep") }
        try {
            assertTrue(StorageTools.updateBytes(context) > 0)
            StorageTools.pruneStaging(context)
            assertFalse(update.exists()); assertFalse(partial.exists()); assertTrue(kept.exists())
            assertFalse(imported.exists())
            update.parentFile!!.mkdirs(); update.writeText("download")
            FileWork.pending.incrementAndGet()
            try { assertThrows(IllegalStateException::class.java) { StorageTools.clean(context, "updates") }; assertTrue(update.exists()) }
            finally { FileWork.pending.decrementAndGet() }
            StorageTools.clean(context, "cache")
            assertTrue(update.exists()); assertFalse(kept.exists())
            StorageTools.clean(context, "updates")
            assertFalse(update.exists())
        } finally { update.delete(); partial.delete(); imported.delete(); kept.delete() }
    }

    @Test fun failedStagingRemovesOnlyTheOwnedCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val copy = File(context.cacheDir, "imports/staging-test.apk").apply { parentFile!!.mkdirs(); writeText("invalid APK") }
        val original = File(context.filesDir, "staging-test.apk").apply { writeText("original file") }
        val pkg = "kr.javajoa.geniebreaker.stagingfixture"
        try {
            SessionInstaller.submit(context, copy, pkg, deleteAfterStaging = true)
            val deadline = SystemClock.uptimeMillis() + 5_000L
            while (FileWork.pending.get() != 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
            assertEquals(0, FileWork.pending.get())
            assertFalse(copy.exists())
            assertEquals("original file", original.readText())
            assertNotNull(SessionInstaller.resultFor(context, pkg))
        } finally {
            copy.delete()
            original.delete()
            SessionInstaller.state(context).edit().remove("result:$pkg").remove("result").apply()
        }
    }

    @Test fun cleanupRefusesActiveFileWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FileWork.reading {
            try { StorageTools.clean(context, "cache"); fail("Cleanup must not overlap a file read") }
            catch (_: IllegalStateException) { }
        }
    }
}
