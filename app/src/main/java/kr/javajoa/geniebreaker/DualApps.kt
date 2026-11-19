package kr.javajoa.geniebreaker

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal object DualPackageNames {
    fun candidate(source: String, index: Int = 0): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).take(10).joinToString("") { "%02x".format(it) }
        return "kr.javajoa.geniebreaker.dual.d$hash" + if (index == 0) "" else ".copy$index"
    }
}
internal data class DualApp(val source: String, val target: String, val id: String, val label: String, val certificate: String)

/** One stable clone per original. Package names, version codes and signing choices stay out of the UI. */
internal class DualApps(private val context: Context) {
    private val preferences = context.getSharedPreferences("dual_apps", Context.MODE_PRIVATE)
    fun record(source: String): DualApp? {
        val value = preferences.getString(source, null) ?: return null
        return try { val json = JSONObject(value); DualApp(source, json.getString("target"), json.getString("id"), json.getString("label"), json.getString("certificate")) } catch (_: Exception) { null }
    }
    fun all(): List<DualApp> = preferences.all.keys.mapNotNull(::record)
    fun isInstalled(packageName: String) = try { context.packageManager.getPackageInfo(packageName, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    @Suppress("DEPRECATION")
    private fun owns(record: DualApp): Boolean {
        if (!isInstalled(record.target)) return true
        return try {
            val pkg = context.packageManager.getPackageInfo(record.target, if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
            val signatures = if (Build.VERSION.SDK_INT >= 28) pkg.signingInfo?.apkContentsSigners else pkg.signatures
            signatures?.size == 1 && MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()).joinToString(":") { "%02X".format(it) } == record.certificate
        } catch (_: Exception) { false }
    }
    fun build(source: PackageInfo, progress: (String) -> Unit): DualApp = build(source, CloneOptions(), progress)
    fun build(source: PackageInfo, options: CloneOptions, progress: (String) -> Unit): DualApp {
        val original = source.packageName
        val previous = record(original)?.takeIf(::owns)
        val target = previous?.target ?: (0..999).asSequence().map { DualPackageNames.candidate(original, it) }.firstOrNull { !isInstalled(it) }
            ?: error("듀얼앱 생성 정보를 준비할 수 없습니다")
        @Suppress("DEPRECATION")
        val installedVersion = try { context.packageManager.getPackageInfo(target, 0).versionCode } catch (_: PackageManager.NameNotFoundException) { 0 }
        @Suppress("DEPRECATION")
        val version = maxOf(1L, source.versionCode.toLong(), installedVersion.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val app = requireNotNull(source.applicationInfo)
        val label = app.loadLabel(context.packageManager).toString().take(72) + if (options.renameApp || options.renameLaunchers) " 듀얼" else ""
        val output = ApkWorkshop(context).build(app, ApkEditSpec(label, target, version, false, options), progress)
        val record = DualApp(original, target, output.id, label, output.certificate)
        preferences.edit().putString(original, JSONObject().put("target", target).put("id", output.id).put("label", label).put("certificate", output.certificate).toString()).commit()
        return record
    }
    fun file(record: DualApp) = File(context.filesDir, "edited/${record.id}.apk")
}
