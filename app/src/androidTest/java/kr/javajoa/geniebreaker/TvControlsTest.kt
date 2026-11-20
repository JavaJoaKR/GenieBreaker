package kr.javajoa.geniebreaker

import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewConfiguration
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
                      source: Int = InputDevice.SOURCE_DPAD, elapsed: Long = 10) =
        KeyEvent(down, down + elapsed, action, key, repeat, modifiers, device, scan, flags, source)

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
            .putString("button.$key", "app:$target").putString("buttonLabel.$key", "설정")
            .putString("button.${KeyEvent.KEYCODE_PROG_BLUE}", "app:${context.packageName}").commit())
    }
    @Test fun ownedPressSurvivesRebindAndNeverRunsTwice() {
        val presses = ButtonPresses<String>()
        var binding: String? = "home"
        val actions = mutableListOf<String>()
        fun press(event: KeyEvent) = presses.handle(event, { ButtonPressActions(binding, null) }, { actions.add(it) })
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key)))
        binding = null
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key, repeat = 1)))
        assertTrue(press(event(KeyEvent.ACTION_UP, key)))
        assertFalse(press(event(KeyEvent.ACTION_UP, key)))
        assertFalse(press(event(KeyEvent.ACTION_DOWN, key)))
        binding = "back"
        assertTrue(press(event(KeyEvent.ACTION_DOWN, key)))
        assertTrue(press(event(KeyEvent.ACTION_UP, key, flags = KeyEvent.FLAG_CANCELED)))
        for (reserved in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_POUND,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_CHANNEL_UP))
            assertFalse(press(event(KeyEvent.ACTION_DOWN, reserved)))
        assertEquals(listOf("home"), actions)
    }
    @Test fun pendingPressesFromTwoRemotesStayIndependentAfterInterrupt() {
        val presses = ButtonPresses<String>()
        val actions = mutableListOf<String>()
        fun press(event: KeyEvent) = presses.handle(event, { ButtonPressActions("home", null) }, { actions.add(it) })
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
        RemoteCapture.claim = { code ->
            ButtonPressActions(
                click = { captured.add(code); Unit },
                hold = { captured.add(holdBindingId(code)); Unit }
            )
        }
        try {
            assertFalse(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = 398)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, appShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, mediaShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, extraButton)))
            RemoteCapture.claim = null
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom, repeat = 1)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, custom,
                elapsed = ViewConfiguration.getLongPressTimeout().toLong())))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, scan = 398)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, appShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, mediaShortcut)))
            assertTrue(RemoteCapture.handle(event(KeyEvent.ACTION_UP, extraButton)))
            assertFalse(RemoteCapture.handle(event(KeyEvent.ACTION_DOWN, custom)))
            assertEquals(listOf(holdBindingId(custom), key, appShortcut, mediaShortcut, extraButton), captured)
        } finally { RemoteCapture.claim = null }
    }
    @Test fun remoteScanBindingRunsOnReleaseAndPreservesSystemAndKeyboardInput() {
        val presses = ButtonPresses<Int>()
        val actions = mutableListOf<Int>()
        val scan = 0x273
        val binding = 0x10000 + scan
        fun press(event: KeyEvent) = presses.handle(event, { ButtonPressActions(it, null) }, { actions.add(it) })
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
    @Test fun unlistedRemoteInputsHaveIndependentClickAndHoldBindings() {
        val presses = ButtonPresses<Int>()
        val actions = mutableListOf<Int>()
        val remote = InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD
        val unlistedScan = 0x2b0
        val hold = ViewConfiguration.getLongPressTimeout().toLong()
        fun press(input: KeyEvent) = presses.handle(input, { ButtonPressActions(it, holdBindingId(it)) }, { actions.add(it) })

        for (scan in listOf(14, unlistedScan)) {
            assertTrue(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = scan, source = remote)))
            assertTrue(press(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, scan = scan, source = remote,
                elapsed = if (scan == 14) 10 else hold)))
        }
        assertFalse(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = 14,
            source = InputDevice.SOURCE_KEYBOARD)))
        for (code in listOf(KeyEvent.KEYCODE_AVR_POWER, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_NUMPAD_9, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND)) {
            assertFalse(press(event(KeyEvent.ACTION_DOWN, code, scan = 588, source = remote)))
            assertNull(buttonLabel(holdBindingId(code)))
        }
        for (scan in listOf(2, 11, 0x200, 0x20a, 0x20b, 0x18b)) {
            assertFalse(press(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scan = scan, source = remote)))
        }
        assertEquals(listOf(0x10000 + 14, holdBindingId(0x10000 + unlistedScan)), actions)
    }

    @Test fun homeShortcutChecksDurationAndCanBeDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val start = SystemClock.uptimeMillis()
            val home = KeyEvent.KEYCODE_HOME
            var enabled = true
            var opened = 0
            var cleared = 0
            val shortcut = HomeShortcut(Handler(Looper.getMainLooper()), { enabled }, { opened++ }, { cleared++ })
            try {
                shortcut.observe(event(KeyEvent.ACTION_DOWN, home, down = start, elapsed = 0))
                shortcut.observe(event(KeyEvent.ACTION_UP, home, down = start, elapsed = 100))
                assertEquals(0, opened)
                shortcut.observe(event(KeyEvent.ACTION_DOWN, home, down = start + 200, elapsed = 0))
                shortcut.observe(event(KeyEvent.ACTION_DOWN, home, down = start + 200, elapsed = 5000, repeat = 1))
                assertEquals(1, opened)
                shortcut.observe(event(KeyEvent.ACTION_DOWN, home, down = start + 200, elapsed = 14999, repeat = 2))
                assertEquals(0, cleared)
                shortcut.observe(event(KeyEvent.ACTION_DOWN, home, down = start + 200, elapsed = 15000, repeat = 3))
                shortcut.observe(event(KeyEvent.ACTION_UP, home, down = start + 200, elapsed = 16000))
                assertEquals(1, opened)
                assertEquals(1, cleared)
                enabled = false
                shortcut.observe(event(KeyEvent.ACTION_DOWN, home, down = start + 17000, elapsed = 0))
                shortcut.observe(event(KeyEvent.ACTION_UP, home, down = start + 17000, elapsed = 16000))
                assertEquals(1, opened)
                assertEquals(1, cleared)
            } finally { shortcut.cancel() }
        }
    }

    @Test fun gProjectCanBeIdentifiedByItsHomeActivityOrApplicationLabel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val home = ResolveInfo().apply {
            nonLocalizedLabel = "기본 홈"
            activityInfo = ActivityInfo().apply {
                applicationInfo = ApplicationInfo().apply { nonLocalizedLabel = "Launcher" }
            }
        }
        assertFalse(isGProjectHome(context, home))
        home.nonLocalizedLabel = "GProject"
        assertTrue(isGProjectHome(context, home))
        home.nonLocalizedLabel = "홈"
        home.activityInfo.applicationInfo.nonLocalizedLabel = "GProject"
        assertTrue(isGProjectHome(context, home))
        assertFalse(isGProjectHome(context, null))
    }

    @Test fun selfLaunchIsAvailableForButtonsAndUnlocksHomeSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = context.packageName
        val key = holdBindingId(KeyEvent.KEYCODE_BUTTON_16)
        val reserved = KeyEvent.KEYCODE_TV_POWER
        val prefs = tvPreferences(context)
        val bindings = RemoteBindings(context)
        assertTrue(launchableApps(context, includeSelf = true).any { it.pkg == pkg })
        assertFalse(launchableApps(context).any { it.pkg == pkg })
        try {
            prefs.edit().putString("button.$reserved", "app:$pkg").commit()
            bindings.register(key)
            assertFalse(bindings.hasAppBinding(pkg))
            bindings.set(key, "app:$pkg", "GenieBreaker")
            assertTrue(bindings.hasAppBinding(pkg))
            bindings.set(key, null, null)
            assertFalse(bindings.hasAppBinding(pkg))
        } finally {
            bindings.set(key, null, null)
            prefs.edit().remove("button.$reserved").commit()
        }
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
