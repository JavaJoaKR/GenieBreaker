package kr.javajoa.geniebreaker

import android.content.ContextWrapper
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class TvControlsTest {
    @Test fun homeRegistrationAndLockSessionAreOptIn() {
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
            assertTrue(lock.enable())
            assertFalse(lock.locked)
            assertTrue(lock.relock())
            assertTrue(lock.locked)
            assertFalse(lock.unlock(BuildConfig.BUILD_COMMIT.dropLast(1)))
            assertTrue(lock.unlock(BuildConfig.BUILD_COMMIT.uppercase(java.util.Locale.ROOT)))
            assertTrue(lock.relock())
            assertTrue(AppLock(context).locked)
        } finally {
            assertTrue(lock.disable())
        }
    }

    @Test fun legacyLockStaysEnabledAndUsesTheCurrentCommit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("app_lock", android.content.Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("pin", "legacy-record").commit())
        val lock = AppLock(context)
        try {
            assertTrue(lock.locked)
            assertTrue(lock.unlock(BuildConfig.BUILD_COMMIT))
            assertTrue(prefs.getBoolean("enabled", false))
            assertFalse(prefs.contains("pin"))
            assertTrue(AppLock(context).locked)
        } finally {
            assertTrue(lock.disable())
        }
    }

    @Test fun malformedSavedBindingDoesNotBlockOtherButtons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = tvPreferences(context)
        val red = KeyEvent.KEYCODE_PROG_RED
        val green = KeyEvent.KEYCODE_PROG_GREEN
        assertTrue(prefs.edit().putInt("button.$red", 1).putString("button.$green", "home").commit())
        val bindings = RemoteBindings(context)
        try {
            assertNull(bindings.action(red))
            assertEquals("home", bindings.action(green))
            bindings.set(red, "back", null)
            assertEquals("back", bindings.action(red))
        } finally {
            prefs.edit().remove("button.$red").remove("button.$green").apply()
        }
    }

    private val key = KeyEvent.KEYCODE_PROG_RED
    private fun event(action: Int, key: Int, repeat: Int = 0, flags: Int = 0,
                      down: Long = 0, device: Int = 1, scan: Int = 0, modifiers: Int = 0,
                      source: Int = InputDevice.SOURCE_DPAD) =
        KeyEvent(down, down + 10, action, key, repeat, modifiers, device, scan, flags, source)

    @Test fun configureEmulatorBoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = InstrumentationRegistry.getArguments().getString("startupFixture")
        org.junit.Assume.assumeNotNull(target)
        assertNotNull(context.packageManager.getLaunchIntentForPackage(target!!))
        StartupApps.select(context, target, "설정")
        val prefs = tvPreferences(context).edit().putBoolean("wake", true)
        if (InstrumentationRegistry.getArguments().getString("startupPending") == "true") {
            prefs.putInt("lastBoot", StartupApps.boot(context) - 1)
        }
        assertTrue(prefs
            .putString("button.$key", "app:$target").putString("buttonLabel.$key", "설정").commit())
    }
    @Test fun ownedPressSurvivesRebindAndNeverRunsTwice() {
        val presses = ButtonPresses<String>()
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
        for (reserved in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOICE_ASSIST))
            assertFalse(press(event(KeyEvent.ACTION_DOWN, reserved)))
        assertEquals(listOf("home"), actions)
    }
    @Test fun pendingPressesFromTwoRemotesStayIndependentAfterInterrupt() {
        val presses = ButtonPresses<String>()
        val actions = mutableListOf<String>()
        fun press(event: KeyEvent) = presses.handle(event, { "home" }, { actions.add(it) })
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key, down = 20, device = 1)))
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key, down = 21, device = 2)))
        assertFalse(press(event(KeyEvent.ACTION_UP, key, down = 1, device = 1)))
        presses.cancelActions()
        assertTrue(press(event(KeyEvent.ACTION_UP, key, down = 20, device = 1)))
        assertTrue(press(event(KeyEvent.ACTION_UP, key, down = 21, device = 2)))
        assertTrue(actions.isEmpty())
        assertFalse(press(event(KeyEvent.ACTION_DOWN, key, modifiers = KeyEvent.META_CTRL_ON)))
    }
    @Test fun foregroundCaptureHandlesVendorKeyAndReleasesOwnership() {
        val custom = 779
        val appShortcut = KeyEvent.KEYCODE_FEATURED_APP_1
        val mediaShortcut = KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
        val extraButton = KeyEvent.KEYCODE_BUTTON_16
        val captured = mutableListOf<Int>()
        RemoteCapture.choose = { code -> captured.add(code); true }
        try {
            assertFalse(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = 398)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, appShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, mediaShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, extraButton)))
            RemoteCapture.choose = null
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom, repeat = 1)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, custom)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, scan = 398)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, appShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, mediaShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, extraButton)))
            assertFalse(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom)))
            assertEquals(listOf(custom, key, appShortcut, mediaShortcut, extraButton), captured)
        } finally { RemoteCapture.choose = null }
    }
    @Test fun remoteScanBindingRunsOnReleaseAndPreservesSystemAndKeyboardInput() {
        val presses = ButtonPresses<Int>()
        val actions = mutableListOf<Int>()
        val scan = 0x273
        val binding = 0x10000 + scan
        fun press(event: KeyEvent) = presses.handle(event, { it }, { actions.add(it) })
        assertFalse(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = scan,
            device = -1, source = InputDevice.SOURCE_KEYBOARD)))
        for (reserved in listOf(103, 114, 116, 0x160, 0x161, 0x164, 0x220, 0x274)) {
            assertFalse("Reserved scan code $reserved",
                press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = reserved)))
        }
        assertFalse(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, scan = scan)))
        assertTrue(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = scan)))
        assertTrue(actions.isEmpty())
        assertTrue(press(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, scan = scan)))
        val oemScan = 0x2e8
        fun hdmi(action: Int) = event(action, KeyEvent.KEYCODE_UNKNOWN, scan = oemScan, source = InputDevice.SOURCE_HDMI)
        assertTrue(press(hdmi(KeyEvent.ACTION_DOWN)))
        assertTrue(press(hdmi(KeyEvent.ACTION_UP)))
        assertFalse(press(hdmi(KeyEvent.ACTION_UP)))
        assertEquals(listOf(binding, 0x10000 + oemScan), actions)
    }
    @Test fun ma4000ShortcutsDoNotClaimKeyboardBackspaceOrTvPower() {
        val presses = ButtonPresses<Int>()
        val actions = mutableListOf<Int>()
        val remote = InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD
        fun press(input: KeyEvent) = presses.handle(input, { it }, { actions.add(it) })

        for ((key, scan) in listOf(KeyEvent.KEYCODE_UNKNOWN to 14, KeyEvent.KEYCODE_AVR_POWER to 588)) {
            assertTrue(press(event(KeyEvent.ACTION_DOWN, key, scan = scan, source = remote)))
            assertTrue(press(event(KeyEvent.ACTION_UP, key, scan = scan, source = remote)))
        }
        assertFalse(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = 14,
            source = InputDevice.SOURCE_KEYBOARD)))
        assertFalse(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, scan = 14, source = remote)))
        assertFalse(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TV_POWER, scan = 588, source = remote)))
        assertEquals(listOf(0x10000 + 14, KeyEvent.KEYCODE_AVR_POWER), actions)
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
            assertTrue(StartupApps.launchIfDue(context, boot + 1, waking = true))
            prefs.edit().putBoolean("wake", false).apply()
            assertFalse(StartupApps.launchIfDue(context, boot + 1, waking = true))
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
