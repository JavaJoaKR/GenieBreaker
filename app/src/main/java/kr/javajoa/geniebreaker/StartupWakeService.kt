package kr.javajoa.geniebreaker

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager

/** Optional ongoing service for TVs where the power button enters standby instead of rebooting. */
class StartupWakeService : Service() {
    private var waitingForWake = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> waitingForWake = true
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (waitingForWake && !getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                        waitingForWake = false
                        StartupApps.present(this@StartupWakeService, true)
                    }
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = "tv_wake"
        manager.createNotificationChannel(NotificationChannel(channel, getString(R.string.startup_wake), NotificationManager.IMPORTANCE_LOW))
        startForeground(912, Notification.Builder(this, channel).setSmallIcon(R.drawable.icon)
            .setContentTitle(getString(R.string.startup_wake)).setContentText(getString(R.string.startup_wake_notice))
            .setOngoing(true).build())
        waitingForWake = !getSystemService(PowerManager::class.java).isInteractive
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (configured(this)) return START_STICKY
        stopSelf()
        return START_NOT_STICKY
    }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private fun configured(context: Context) = tvPreferences(context).let {
            it.contains("startup") && it.getBoolean("wake", false) && StartupApps.allowed(context)
        }
        fun restore(context: Context): Boolean {
            if (!configured(context)) return false
            return try { context.startForegroundService(Intent(context, StartupWakeService::class.java)); true }
            catch (_: IllegalStateException) { false } catch (_: SecurityException) { false }
        }
    }
}
