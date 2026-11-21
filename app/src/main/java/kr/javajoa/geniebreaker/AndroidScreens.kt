package kr.javajoa.geniebreaker

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

internal fun openScreen(context: Context, intent: Intent?): Boolean {
    if (intent == null) return false
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

internal fun launchIntent(context: Context, pkg: String): Intent? {
    if (pkg == context.packageName || pkg.isBlank()) return null
    val pm = context.packageManager
    return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
}

internal fun launchPackage(context: Context, pkg: String): Boolean =
    openScreen(context, launchIntent(context, pkg))

internal fun openAppsScreen(context: Context): Boolean =
    openScreen(context, Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) ||
        openScreen(context, Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))

internal fun openBluetoothScreen(context: Context): Boolean {
    val pm = context.packageManager
    val packages = pm.queryIntentActivities(Intent(Settings.ACTION_SETTINGS),
        PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo.packageName } +
        listOf("com.android.tv.settings", "com.google.android.tv.settings", "com.google.android.apps.tv.settings")
    val listScreens = setOf(
        "AccessoriesActivity", "RemoteAndAccessoriesActivity", "RemotesAndAccessoriesActivity",
        "BluetoothDevicesActivity", "BluetoothDeviceListActivity"
    )
    val listLabels = setOf("remotes & accessories", "remote & accessories", "remotes and accessories",
        "bluetooth device list", "리모컨 및 액세서리", "블루투스 기기 목록")
    for (pkg in packages.distinct()) {
        @Suppress("DEPRECATION")
        val info = try { pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES) }
        catch (_: PackageManager.NameNotFoundException) { continue }
        val flags = info.applicationInfo?.flags ?: continue
        if (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue
        for (screen in info.activities.orEmpty()) {
            if (!screen.exported || !screen.enabled) continue
            val componentName = screen.name.substringAfterLast('.')
            val activityLabel = if (screen.labelRes != 0 || screen.nonLocalizedLabel != null)
                screen.loadLabel(pm).toString().trim().lowercase() else ""
            if (componentName !in listScreens && activityLabel !in listLabels) continue
            if (screen.permission != null && context.checkSelfPermission(screen.permission) != PackageManager.PERMISSION_GRANTED) continue
            if (openScreen(context, Intent().setComponent(ComponentName(pkg, screen.name)))) return true
        }
    }
    return false
}

internal fun openAccessibilityScreen(context: Context): Boolean =
    openScreen(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

internal fun openAccountSettings(context: Context): Boolean =
    openScreen(context, Intent(Settings.ACTION_SYNC_SETTINGS)) ||
        openScreen(context, Intent("android.settings.ACCOUNT_SYNC_SETTINGS"))

internal fun openTvUpdateSettings(context: Context): Boolean {
    // Android TV used this action before it became a public SDK constant.
    val update = Intent("android.settings.SYSTEM_UPDATE_SETTINGS")
    val handlers = context.packageManager.queryIntentActivities(
        update, PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY
    )
    return handlers.asSequence()
        .filter { it.priority > 0 && it.activityInfo.exported }
        .sortedByDescending { it.priority }
        .any {
            val activity = it.activityInfo
            openScreen(context, Intent(update).setComponent(ComponentName(activity.packageName, activity.name)))
        } || openScreen(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
}

internal fun openAppDetails(context: Context, pkg: String): Boolean =
    openScreen(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))

internal data class LaunchableApp(val pkg: String, val label: String)

internal fun launchableApps(context: Context, includeSelf: Boolean = false): List<LaunchableApp> {
    val pm = context.packageManager
    return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
        .filter { it.activityInfo.exported && (includeSelf || it.activityInfo.packageName != context.packageName) }
        .distinctBy { it.activityInfo.packageName }
        .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .sortedWith(compareBy({ it.label.lowercase() }, { it.pkg }))
}
