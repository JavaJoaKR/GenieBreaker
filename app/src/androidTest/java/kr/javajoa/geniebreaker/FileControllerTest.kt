package kr.javajoa.geniebreaker

import androidx.test.platform.app.InstrumentationRegistry
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileControllerTest {
    @Test fun explorerCopiesMovesAndRefusesOverwriteTraversalAndRootDeletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val browser = FileController(activity) {}
        val root = File(activity.filesDir, "browser")
        val fixture = File(root, "file-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val base = "local:${fixture.name}"
        try {
            assertTrue(FileController.isLocalDocument(android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload")))
            assertFalse(FileController.isLocalDocument(android.net.Uri.parse("content://cloud.example.documents/tree/private")))
            browser.listing(""); browser.listing("local:")
            File(fixture, "source.txt").writeText("exact contents")
            browser.listing(base)
            browser.mutate(mapOf("action" to "mkdir", "path" to base, "name" to "destination"))
            browser.mutate(mapOf("action" to "copy", "path" to "$base/source.txt"))
            browser.mutate(mapOf("action" to "paste", "path" to "$base/destination"))
            assertEquals("exact contents", File(fixture, "destination/source.txt").readText())
            assertTrue(File(fixture, "source.txt").exists())
            assertThrows(IllegalArgumentException::class.java) { browser.mutate(mapOf("action" to "paste", "path" to "$base/destination")) }
            assertEquals("exact contents", File(fixture, "destination/source.txt").readText())
            browser.mutate(mapOf("action" to "rename", "path" to "$base/source.txt", "parent" to base, "name" to "moved.txt"))
            browser.mutate(mapOf("action" to "cut", "path" to "$base/moved.txt"))
            browser.mutate(mapOf("action" to "paste", "path" to "$base/destination"))
            assertFalse(File(fixture, "moved.txt").exists())
            assertEquals("exact contents", File(fixture, "destination/moved.txt").readText())
            assertThrows(IllegalArgumentException::class.java) { browser.local("local:../../outside") }
            assertThrows(IllegalArgumentException::class.java) { browser.mutate(mapOf("action" to "delete", "path" to "local:")) }
            assertThrows(IllegalArgumentException::class.java) { browser.mutate(mapOf("action" to "mkdir", "path" to base, "name" to "../escape")) }
            browser.listing(base)
            browser.mutate(mapOf("action" to "copy", "path" to "$base/destination"))
            assertThrows(IllegalArgumentException::class.java) { browser.mutate(mapOf("action" to "paste", "path" to "$base/destination")) }
        } finally { fixture.deleteRecursively(); browser.close(); instrumentation.runOnMainSync { activity.finish() } }
    }
}
