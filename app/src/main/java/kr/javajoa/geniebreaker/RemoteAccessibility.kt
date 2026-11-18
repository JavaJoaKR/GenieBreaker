package kr.javajoa.geniebreaker

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
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
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (watching) changed()
        }
    }

    fun start() {
        if (watching) return
        watching = true
        RemoteServiceConnection.addListener(changed)
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
        RemoteServiceConnection.removeListener(changed)
        if (observingSettings) {
            observingSettings = false
            resolver.unregisterContentObserver(observer)
        }
    }

    fun status(): String {
        if (RemoteServiceConnection.connected) return "active"
        return try {
            val enabled = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
                Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
            if (enabled) "connecting" else "off"
        } catch (_: SecurityException) {
            "unknown"
        }
    }
}
