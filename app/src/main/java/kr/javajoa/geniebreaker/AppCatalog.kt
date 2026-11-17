package kr.javajoa.geniebreaker

import android.content.Context
import android.content.pm.*

/** Lists packages and prepares the display rows on the existing package worker. */
internal class AppCatalog(private val context: Context, private val duals: DualApps) {
    private val pm = context.packageManager
    private val icons = android.util.LruCache<String, ByteArray>(128)
    data class Listing(val packages: List<PackageInfo>, val entries: List<Map<String, Any>>)

    fun list(dualFilter: Boolean, allFilter: Boolean): Listing {
        val records = duals.all()
        val bySource = records.associateBy { it.source }
        val byTarget = records.associateBy { it.target }
        val items = if (dualFilter) records.mapNotNull { record ->
            val installed = try { pm.getPackageInfo(record.source, 0) } catch (_: PackageManager.NameNotFoundException) {
                try { pm.getPackageInfo(record.target, 0) } catch (_: PackageManager.NameNotFoundException) { null }
            }
            installed ?: pm.getPackageArchiveInfo(duals.file(record).absolutePath, 0)?.apply {
                applicationInfo?.sourceDir = duals.file(record).absolutePath
                applicationInfo?.publicSourceDir = duals.file(record).absolutePath
            }
        }.sortedBy { it.applicationInfo?.loadLabel(pm).toString().lowercase() }
        else pm.getInstalledPackages(0).filter { pkg ->
            pkg.packageName != context.packageName && pkg.packageName !in byTarget && pkg.applicationInfo != null &&
                (allFilter || pm.getLeanbackLaunchIntentForPackage(pkg.packageName) != null || pm.getLaunchIntentForPackage(pkg.packageName) != null)
        }.sortedBy { it.applicationInfo!!.loadLabel(pm).toString().lowercase() }
        val next = items.map { pkg ->
            val record = bySource[pkg.packageName] ?: byTarget[pkg.packageName]
            mapOf<String, Any>("id" to pkg.packageName, "name" to (pkg.applicationInfo?.loadLabel(pm)?.toString() ?: "앱"), "icon" to icon(pkg.applicationInfo, pkg.lastUpdateTime),
                "detail" to when { record == null -> ""; duals.isInstalled(record.target) -> "듀얼앱 사용 중"; else -> "설치 준비됨" })
        }
        return Listing(items, next)
    }

    private fun icon(info: ApplicationInfo?, version: Long): ByteArray {
        if (info == null) return byteArrayOf()
        val key = "${info.packageName}:$version"
        icons.get(key)?.let { return it }
        return try {
            val drawable = info.loadIcon(pm)
            val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 64, 64); drawable.draw(android.graphics.Canvas(bitmap))
            val bytes = java.io.ByteArrayOutputStream().use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            bitmap.recycle(); icons.put(key, bytes); bytes
        } catch (_: Exception) { byteArrayOf() }
    }
    fun clear() = icons.evictAll()
}
