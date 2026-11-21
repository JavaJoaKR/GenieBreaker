package kr.javajoa.geniebreaker

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AppUpdaterTest {
    private fun release(code: Int, channel: String = "main", state: String = "uploaded", prerelease: Boolean = false) = """{"prerelease":$prerelease,"body":"[channel:$channel]","assets":[{"name":"GenieBreaker-$code-abcdef0.apk","state":"$state","browser_download_url":"https://github.com/JavaJoaKR/GenieBreaker/releases/download/build-$code/GenieBreaker-$code-abcdef0.apk"}]}"""
    @Test fun catalogChoosesVersionRatherThanArrayOrderOrOtherBranches() {
        val latest = ReleaseCatalog.latest(JSONArray("[${release(8)},${release(99, "feature")},${release(7)},${release(12)},${release(20, state = "new")},${release(133, prerelease = true)}]"))
        assertEquals(12L, latest.version)
        try { ReleaseCatalog.latest(JSONArray("[]")); fail("Empty releases must not mean latest version") } catch (_: IllegalStateException) { }
    }
    @Test fun firstNetworkFailureAutomaticallyRetriesWithoutSixHourSuppression() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("updates", 0)
        val old = preferences.getLong("last_check", 0)
        // A clock correction must not suppress checks until the old wall time catches up.
        preferences.edit().putLong("last_check", System.currentTimeMillis() + 86_400_000L).commit()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).putExtra("ui_preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val attempts = AtomicInteger()
        val requestTimes = mutableListOf<Long>()
        val done = CountDownLatch(1)
        var updater: AppUpdater? = null
        try {
            instrumentation.runOnMainSync {
                updater = AppUpdater(activity, catalogLoader = {
                    requestTimes += SystemClock.elapsedRealtime()
                    if (attempts.incrementAndGet() == 1) throw java.io.IOException("first request offline")
                    JSONArray("[${release(1)}]")
                }, retryDelays = longArrayOf(50)) { text, _ ->
                    if (text == "업데이트 확인" && attempts.get() == 2) done.countDown()
                }
                updater!!.checkForUpdate(true)
            }
            assertTrue("First failure must retry automatically", done.await(10, TimeUnit.SECONDS))
            assertEquals(2, attempts.get())
            assertTrue("Retry must wait for the five-second check cooldown", requestTimes[1] - requestTimes[0] >= 4_990L)
            assertTrue(preferences.getLong("last_check", 0) > 0)
        } finally {
            instrumentation.runOnMainSync { updater?.close(); activity.finish() }
            preferences.edit().putLong("last_check", old).commit()
        }
    }
    @Test fun logsContainCodesAndNeverRawDeviceMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Diagnostics.clear(context)
        val code = Diagnostics.record(context, "Launch 192.168.1.10 secret-device", IllegalStateException("AA:BB:CC:DD:EE:FF /storage/private secret"))
        val text = Diagnostics.read(context)
        assertTrue(text.contains(code)); assertTrue(text.contains("IllegalStateException"))
        for (value in listOf("192.168", "secret", "AA:BB", "/storage/")) assertFalse(text.contains(value))
    }

    @Test fun diagnosticsShowOperationAndBlockerWithoutRawErrors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Diagnostics.clear(context)
        val rejected = "Bluetooth: 요청 거절 AA:BB:CC:DD:EE:FF"
        val unknown = Diagnostics.record(context, rejected, step = DiagnosticStep.BLUETOOTH_PAIR,
            cause = DiagnosticCause.REASON_UNAVAILABLE)
        assertTrue(unknown == "GB-BT-7394")
        Diagnostics.record(context, rejected, step = DiagnosticStep.BLUETOOTH_CONNECT,
            phase = DiagnosticStep.BLUETOOTH_PROFILE, cause = DiagnosticCause.CONNECT_APP_OP)
        Diagnostics.record(context, "Accessibility restricted settings blocked", step = DiagnosticStep.ACCESSIBILITY,
            cause = DiagnosticCause.ACCESSIBILITY_RESTRICTED)
        Diagnostics.record(context, "Bluetooth: 요청을 전송했습니다.", step = DiagnosticStep.BLUETOOTH_FORGET)

        val causes = Diagnostics.recentCauses(context).joinToString("\n")
        assertTrue(causes.contains("페어링 · ${DiagnosticCause.REASON_UNAVAILABLE.label} [GB-BT-7394]"))
        assertTrue(causes.contains("연결 / 프로필 준비 · ${DiagnosticCause.CONNECT_APP_OP.label} [GB-BT-7394]"))
        assertTrue(causes.contains("접근성 · ${DiagnosticCause.ACCESSIBILITY_RESTRICTED.label}"))
        assertFalse(causes.contains("AA:BB"))
        assertFalse(causes.contains("등록 삭제"))
        assertFalse(causes.contains("BT Rejected"))
    }
}
