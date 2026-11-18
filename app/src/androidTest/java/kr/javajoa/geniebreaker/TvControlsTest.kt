package kr.javajoa.geniebreaker

import android.content.ContextWrapper
import android.content.Intent
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class TvControlsTest {
    @Test fun homeRegistrationAndPinSessionAreOptIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val homes = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        assertTrue(homes.any { it.activityInfo.name == MainActivity::class.java.name })
        assertFalse("Installing the app must not replace the current HOME", isDefaultHome(context))

        val lock = AppLock(context)
        try {
            assertFalse(lock.enabled)
            assertFalse(lock.setPin("012"))
            assertTrue(lock.setPin("02468"))
            assertFalse(lock.locked)
            assertTrue(lock.relock())
            assertTrue(lock.locked)
            assertFalse(lock.unlock("99999"))
            assertTrue(lock.unlock("02468"))
            assertTrue(lock.relock())
            assertTrue(lock.unlock(BuildConfig.VERSION_NAME.replace(".", "")))
            assertTrue(AppLock(context).locked)
        } finally {
            assertTrue(lock.disable())
        }
    }

    private val key = KeyEvent.KEYCODE_PROG_RED
    private fun event(action: Int, key: Int, repeat: Int = 0, flags: Int = 0) =
        KeyEvent(0, 10, action, key, repeat, 0, 1, 0, flags)

    @Test fun configureEmulatorBoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = InstrumentationRegistry.getArguments().getString("startupFixture")
        org.junit.Assume.assumeNotNull(target)
        assertNotNull(context.packageManager.getLaunchIntentForPackage(target!!))
        StartupApps.select(context, target, "설정")
        assertTrue(tvPreferences(context).edit().putBoolean("wake", true)
            .putString("button.$key", "app:$target").putString("buttonLabel.$key", "설정").commit())
    }
    @Test fun ownedPressSurvivesRebindAndNeverRunsTwice() {
        val presses = ButtonPresses()
        var binding: String? = "home"
        val actions = mutableListOf<String>()
        fun press(event: KeyEvent) = presses.handle(event, { binding }, { actions.add(it) })
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key)))
        binding = null
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key, repeat = 1)))
        assertTrue(press(event(KeyEvent.ACTION_UP, key)))
        assertFalse(press(event(KeyEvent.ACTION_UP, key)))
        assertFalse(press(event(KeyEvent.ACTION_DOWN, key)))
        binding = "back"
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key)))
        assertTrue(press(event(KeyEvent.ACTION_UP, key, flags = KeyEvent.FLAG_CANCELED)))
        for (reserved in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_VOLUME_UP))
            assertFalse(press(event(KeyEvent.ACTION_DOWN, reserved)))
        assertEquals(listOf("home"), actions)
    }
    @Test fun foregroundCaptureHandlesVendorKeyAndReleasesOwnership() {
        val custom = 779
        val captured = mutableListOf<Int>()
        RemoteCapture.choose = { code -> captured.add(code); true }
        try {
            assertFalse(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom)))
            RemoteCapture.choose = null
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom, repeat = 1)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, custom)))
            assertFalse(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom)))
            assertEquals(listOf(custom), captured)
        } finally { RemoteCapture.choose = null }
    }
    @Test fun startupSelectionSkipsCurrentBootAndHandsOffOnceOnNextBoot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val launches = mutableListOf<Intent>()
        val context = object : ContextWrapper(target) {
            override fun startActivity(intent: Intent) { launches.add(intent) }
        }
        val pkg = target.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .first { it.activityInfo.packageName != target.packageName }.activityInfo.packageName
        val prefs = tvPreferences(context)
        val command = "appops set " + target.packageName + " SYSTEM_ALERT_WINDOW allow"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        try {
            StartupApps.select(context, pkg, "샘플")
            val boot = StartupApps.boot(context)
            assertFalse(StartupApps.launchIfDue(context, boot))
            assertTrue(StartupApps.launchIfDue(context, boot + 1))
            assertFalse(StartupApps.launchIfDue(context, boot + 1))
            assertFalse(StartupApps.launchIfDue(context, boot + 1, waking = true))
            prefs.edit().putBoolean("wake", true).apply()
            assertTrue(StartupApps.launchIfDue(context, boot + 1, waking = true))
            assertEquals(listOf(pkg, pkg), launches.map { it.component?.packageName })
            StartupApps.clear(context)
            assertFalse(StartupApps.launchIfDue(context, boot + 2))
        } finally {
            prefs.edit().clear().commit()
            val reset = "appops set " + target.packageName + " SYSTEM_ALERT_WINDOW default"
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(reset)).use { it.readBytes() }
        }
    }
}
