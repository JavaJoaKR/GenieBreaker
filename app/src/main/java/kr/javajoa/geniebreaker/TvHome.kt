package kr.javajoa.geniebreaker

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings

internal fun resolveDefaultHome(context: Context): ResolveInfo? =
    context.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY
    )

internal fun isDefaultHome(context: Context): Boolean =
    resolveDefaultHome(context)?.activityInfo?.packageName == context.packageName

internal fun isGProjectHome(context: Context, home: ResolveInfo? = resolveDefaultHome(context)): Boolean {
    val activity = home?.activityInfo ?: return false
    val packages = context.packageManager
    return home.loadLabel(packages).toString().trim().equals("GProject", ignoreCase = true) ||
        activity.applicationInfo?.loadLabel(packages)?.toString()?.trim()?.equals("GProject", ignoreCase = true) == true
}

internal fun openHomeSettings(activity: Activity): Boolean {
    if (openScreen(activity, Intent(Settings.ACTION_HOME_SETTINGS))) return true
    if (Build.VERSION.SDK_INT >= 29) {
        val roles = activity.getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 611)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
    return openScreen(activity, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}
