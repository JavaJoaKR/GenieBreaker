package kr.javajoa.geniebreaker

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import java.io.ByteArrayOutputStream

/** One package inventory supplies the launcher and the app-management filters. */
internal class AppCatalog(private val context: Context, private val duals: DualApps) {
    private val pm = context.packageManager
    private val icons = object : LruCache<IconKey, ByteArray>(1024 * 1024) {
        override fun sizeOf(key: IconKey, value: ByteArray): Int = value.size + 1
    }

    private data class IconKey(
        val packageName: String,
        val source: String?,
        val updatedAt: Long,
        val languageTags: String
    )
    private data class InstalledApps(
        val revision: Long,
        val packages: Map<String, PackageInfo>,
        val launchable: Set<String>,
        val launcherRows: List<Map<String, Any>>
    )

    data class Listing(
        val packages: List<PackageInfo>,
        val entries: List<Map<String, Any>>,
        val launcherRows: List<Map<String, Any>>
    )

    // Accessed only on the package worker.
    private var inventory: InstalledApps? = null

    @Suppress("DEPRECATION")
    private fun installedApps(revision: Long): InstalledApps {
        inventory?.takeIf { it.revision == revision }?.let { return it }
        val packages = pm.getInstalledPackages(0).associateBy { it.packageName }
        val candidates = launchableApps(context).filter { it.pkg in packages }
        val launcherRows = candidates.mapNotNull { candidate ->
            val pkg = packages.getValue(candidate.pkg)
            val app = pkg.applicationInfo ?: return@mapNotNull null
            if (!app.enabled || app.flags and ApplicationInfo.FLAG_SUSPENDED != 0) return@mapNotNull null
            row(pkg, candidate.label)
        }
        val launchable = candidates.mapTo(mutableSetOf()) { it.pkg }
        // getLaunchIntentForPackage also accepts apps with a CATEGORY_INFO front door.
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_INFO), 0)
            .mapTo(launchable) { it.activityInfo.packageName }
        return InstalledApps(revision, packages, launchable, launcherRows).also { inventory = it }
    }

    @Suppress("DEPRECATION")
    fun list(dualFilter: Boolean, allFilter: Boolean, revision: Long): Listing {
        val installed = installedApps(revision)
        val records = duals.all()
        val bySource = records.associateBy { it.source }
        val byTarget = records.associateBy { it.target }
        val items = if (dualFilter) {
            records.mapNotNull { record ->
                installed.packages[record.source] ?: installed.packages[record.target] ?: run {
                    val sourcePath = duals.file(record).absolutePath
                    pm.getPackageArchiveInfo(sourcePath, 0)?.apply {
                        applicationInfo?.sourceDir = sourcePath
                        applicationInfo?.publicSourceDir = sourcePath
                    }
                }
            }
        } else {
            installed.packages.values.filter { pkg ->
                pkg.packageName != context.packageName && pkg.packageName !in byTarget &&
                    pkg.applicationInfo != null && (allFilter || pkg.packageName in installed.launchable)
            }
        }
        val labeled = items.map { pkg ->
            pkg to (pkg.applicationInfo?.loadLabel(pm)?.toString() ?: "앱")
        }.sortedWith(compareBy({ it.second.lowercase() }, { it.first.packageName }))
        val entries = labeled.map { (pkg, label) ->
            val record = bySource[pkg.packageName] ?: byTarget[pkg.packageName]
            val detail = when {
                record == null -> ""
                record.target in installed.packages -> "듀얼앱 설치됨"
                else -> "APK 설치 가능"
            }
            row(pkg, label) + ("detail" to detail)
        }
        return Listing(labeled.map { it.first }, entries, installed.launcherRows)
    }

    private fun row(pkg: PackageInfo, label: String): Map<String, Any> =
        mapOf("id" to pkg.packageName, "name" to label, "icon" to icon(pkg.applicationInfo, pkg.lastUpdateTime))

    private fun icon(info: ApplicationInfo?, updatedAt: Long): ByteArray {
        if (info == null) return byteArrayOf()
        val key = IconKey(info.packageName, info.sourceDir, updatedAt, context.resources.configuration.locales.toLanguageTags())
        icons.get(key)?.let { return it }
        return try {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try {
                val drawable = info.loadIcon(pm)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
                val bytes = ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                }
                icons.put(key, bytes)
                bytes
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            byteArrayOf()
        }
    }
}
