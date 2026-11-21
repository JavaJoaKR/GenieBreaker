package kr.javajoa.geniebreaker

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings

/** Main-thread connection state. A late unbind must not clear a newer connection. */
internal object RemoteServiceConnection {
    private var service: RemoteButtonService? = null
    private val listeners = mutableSetOf<() -> Unit>()
    val connected: Boolean get() = service != null

    fun connect(current: RemoteButtonService) {
        if (service === current) return
        service = current
        listeners.toList().forEach { it() }
    }

    fun disconnect(current: RemoteButtonService): Boolean {
        if (service !== current) return false
        service = null
        listeners.toList().forEach { it() }
        return true
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
}

internal class RemoteAccessibility(private val context: Context, private val changed: () -> Unit) {
    private val component = ComponentName(context, RemoteButtonService::class.java)
    private val resolver = context.contentResolver
    private var watching = false
    private var observingSettings = false
    private var reportedRestriction = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable {
        if (watching) {
            try {
                changed()
            } catch (error: RuntimeException) {
                Diagnostics.record(context, "Accessibility status refresh", error)
            }
        }
    }
    private val scheduleRefresh: () -> Unit = {
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (watching) scheduleRefresh()
        }
    }

    fun start() {
        if (watching) return
        watching = true
        RemoteServiceConnection.addListener(scheduleRefresh)
        for (setting in listOf(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, Settings.Secure.ACCESSIBILITY_ENABLED)) {
            try {
                resolver.registerContentObserver(Settings.Secure.getUriFor(setting), false, observer)
                observingSettings = true
            } catch (error: SecurityException) {
                Diagnostics.record(context, "Accessibility settings observer", error)
            }
        }
    }

    fun stop() {
        if (!watching) return
        watching = false
        RemoteServiceConnection.removeListener(scheduleRefresh)
        handler.removeCallbacks(refresh)
        if (observingSettings) {
            observingSettings = false
            resolver.unregisterContentObserver(observer)
        }
    }

    fun status(): String {
        if (RemoteServiceConnection.connected) {
            reportedRestriction = false
            return "active"
        }
        if (hasRestrictedSettings()) {
            if (!reportedRestriction) {
                reportedRestriction = true
                Diagnostics.record(context, "Accessibility restricted settings blocked", step = DiagnosticStep.ACCESSIBILITY,
                    cause = DiagnosticCause.ACCESSIBILITY_RESTRICTED)
            }
            return "restricted"
        }
        reportedRestriction = false
        return try {
            // During rebind the global bit can lag behind the per-service selection.
            val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
            if (enabled) "connecting" else "off"
        } catch (_: SecurityException) {
            "unknown"
        }
    }

    @Suppress("DEPRECATION")
    private fun hasRestrictedSettings(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return try {
            // This optional app-op is only consulted for our own package. Some TV
            // firmware omits it or disallows reading it from an app.
            val mode = context.getSystemService(AppOpsManager::class.java)?.unsafeCheckOpNoThrow(
                "android:access_restricted_settings", context.applicationInfo.uid, context.packageName
            )
            mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
