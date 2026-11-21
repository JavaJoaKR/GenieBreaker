package kr.javajoa.geniebreaker

import android.Manifest.permission
import android.app.AppOpsManager
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager

internal fun bluetoothBlocker(
    context: Context,
    adapter: BluetoothAdapter?,
    step: DiagnosticStep,
    error: Throwable? = null
): DiagnosticCause? {
    val search = step == DiagnosticStep.BLUETOOTH_SCAN || step == DiagnosticStep.BLUETOOTH_COMPANION
    if (error is SecurityException) {
        val required = error.message.orEmpty()
        when {
            "android.permission.BLUETOOTH_PRIVILEGED" in required -> return DiagnosticCause.PRIVILEGED_BLUETOOTH
            permission.BLUETOOTH_SCAN in required -> return DiagnosticCause.SCAN_PERMISSION
            permission.BLUETOOTH_CONNECT in required -> return DiagnosticCause.CONNECT_PERMISSION
            permission.ACCESS_FINE_LOCATION in required -> return DiagnosticCause.LOCATION_PERMISSION
        }
    }
    if (context.getSystemService(UserManager::class.java)?.hasUserRestriction(UserManager.DISALLOW_BLUETOOTH) == true) {
        return DiagnosticCause.DEVICE_POLICY
    }
    if (Build.VERSION.SDK_INT >= 31) {
        if (search && !context.hasPermission(permission.BLUETOOTH_SCAN)) return DiagnosticCause.SCAN_PERMISSION
        if (!context.hasPermission(permission.BLUETOOTH_CONNECT)) return DiagnosticCause.CONNECT_PERMISSION
        if (search && context.appOpDenied(permission.BLUETOOTH_SCAN)) return DiagnosticCause.SCAN_APP_OP
        if (context.appOpDenied(permission.BLUETOOTH_CONNECT)) return DiagnosticCause.CONNECT_APP_OP
    } else if (search && !context.hasPermission(permission.ACCESS_FINE_LOCATION)) {
        return DiagnosticCause.LOCATION_PERMISSION
    }
    when (error) {
        is SecurityException -> return DiagnosticCause.SYSTEM_PERMISSION
        is NoSuchMethodException, is IllegalAccessException -> return DiagnosticCause.SYSTEM_FEATURE_UNAVAILABLE
        is ActivityNotFoundException -> return DiagnosticCause.SCREEN_UNAVAILABLE
    }
    if (adapter == null) return DiagnosticCause.BLUETOOTH_UNAVAILABLE
    try {
        if (step != DiagnosticStep.BLUETOOTH_ENABLE && !adapter.isEnabled) return DiagnosticCause.BLUETOOTH_DISABLED
    } catch (_: SecurityException) {
        return DiagnosticCause.SYSTEM_PERMISSION
    }
    return null
}

internal fun bluetoothSettingsRestriction(context: Context): DiagnosticCause? {
    val users = context.getSystemService(UserManager::class.java) ?: return null
    return when {
        users.hasUserRestriction(UserManager.DISALLOW_BLUETOOTH) -> DiagnosticCause.DEVICE_POLICY
        users.hasUserRestriction(UserManager.DISALLOW_CONFIG_BLUETOOTH) -> DiagnosticCause.BLUETOOTH_SETTINGS_RESTRICTED
        else -> null
    }
}

private fun Context.hasPermission(name: String): Boolean =
    checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
private fun Context.appOpDenied(permission: String): Boolean {
    val op = AppOpsManager.permissionToOp(permission) ?: return false
    val manager = getSystemService(AppOpsManager::class.java) ?: return false
    return try {
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            manager.unsafeCheckOpNoThrow(op, applicationInfo.uid, packageName)
        } else {
            manager.checkOpNoThrow(op, applicationInfo.uid, packageName)
        }
        mode == AppOpsManager.MODE_ERRORED || mode == AppOpsManager.MODE_IGNORED
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
