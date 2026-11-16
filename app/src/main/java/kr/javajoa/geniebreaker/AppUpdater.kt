package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Startup update checks. Downloads are automatic; installation always requires user consent. */
internal class AppUpdater(
    private val activity: Activity,
    private val catalogLoader: (() -> JSONArray)? = null,
    private val retryDelays: LongArray = longArrayOf(5000L, 15000L, 45000L),
    private val state: (String, Boolean) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val preferences = activity.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val apk = File(activity.cacheDir, "updates/update.apk")
    private var waitingForPermission = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retryCount = 0
    @Volatile private var failed = false
    @Volatile private var closed = false
    private val retry = Runnable { if (!closed) checkForUpdate(automatic = true) }
    private fun succeeded() { failed = false; retryCount = 0; handler.removeCallbacks(retry) }
    private fun scheduleRetry() {
        if (closed || retryCount >= retryDelays.size) return
        val delay = retryDelays[retryCount++]
        handler.removeCallbacks(retry); handler.postDelayed(retry, delay)
    }

    fun checkForUpdate(automatic: Boolean = false) {
        if (closed) return
        if (!busy.compareAndSet(false, true)) return
        handler.removeCallbacks(retry)
        ui { state("업데이트 확인 중", false) }
        executor.execute {
            FileWork.lock.readLock().lock()
            try {
                if (apk.exists()) {
                    if (validUpdate(apk)) { succeeded(); ui { state("업데이트 설치", true) }; return@execute }
                    apk.delete()
                }
                // A cached APK must be detected before applying the successful-check interval.
                if (automatic && !failed && System.currentTimeMillis() - preferences.getLong("last_check", 0) < 6 * 60 * 60 * 1000) {
                    ui { state("업데이트 확인", true) }; return@execute
                }
                val releases = catalogLoader?.invoke() ?: run {
                    val connection = connect("https://api.github.com/repos/JavaJoaKR/GenieBreaker/releases?per_page=100")
                    try { connection.inputStream.bufferedReader().use { JSONArray(it.readText()) } }
                    finally { connection.disconnect() }
                }
                val installed = version(activity.packageManager.getPackageInfo(activity.packageName, 0))
                val latest = ReleaseCatalog.latest(releases)
                val candidate = latest.url
                if (latest.version <= installed) {
                    succeeded()
                    preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    ui { state("업데이트 확인", true); if (!automatic) toast("최신 버전입니다") }
                    return@execute
                }
                ui { state("업데이트 받는 중", false) }
                apk.parentFile?.mkdirs()
                val temporary = File.createTempFile("download-", ".apk", apk.parentFile)
                try {
                    val download = connect(candidate)
                    try {
                        require(download.contentLength.toLong() <= 128L * 1024 * 1024) { "업데이트 파일이 너무 큽니다" }
                        download.inputStream.use { input -> temporary.outputStream().use { output ->
                            val buffer = ByteArray(8192); var total = 0L
                            while (true) {
                                check(!Thread.currentThread().isInterrupted)
                                val count = input.read(buffer); if (count < 0) break
                                total += count; require(total <= 128L * 1024 * 1024)
                                output.write(buffer, 0, count)
                            }
                        } }
                    } finally { download.disconnect() }
                    require(validUpdate(temporary)) { "앱 ID·버전 또는 서명이 일치하지 않습니다" }
                    check(temporary.renameTo(apk)) { "업데이트 파일을 저장하지 못했습니다" }
                    succeeded()
                    preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    ui { state("업데이트 설치", true); if (!automatic) toast("다운로드 완료. 설치 버튼을 선택하세요") }
                } finally { temporary.delete() }
            } catch (error: Exception) {
                if (!closed) {
                    failed = true
                    Diagnostics.record(activity, "Update check", error)
                    ui { state("업데이트 다시 확인", true); if (!automatic) toast("확인하지 못했습니다. 잠시 후 자동으로 다시 시도합니다.") }
                }
            } finally {
                busy.set(false); FileWork.lock.readLock().unlock()
                if (failed && !closed) scheduleRetry()
            }
        }
    }
    fun click() {
        if (busy.get()) return
        if (apk.exists()) install() else checkForUpdate()
    }
    private fun install() {
        try {
            require(validUpdate(apk))
            if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                TvMenus.show(activity, "앱 업데이트 허용", listOf("나중에", "허용 화면 열기"), "업데이트를 설치하려면 이 앱의 ‘알 수 없는 앱 설치’를 허용해야 합니다.") { choice ->
                    if (choice == 1) try {
                        waitingForPermission = true
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    } catch (_: Exception) { waitingForPermission = false; toast("펌웨어에서 설치 허용 화면을 지원하지 않습니다") }
                }
                return
            }
            SessionInstaller.submit(activity, apk, activity.packageName)
        } catch (_: ActivityNotFoundException) { toast("이 펌웨어에는 사용할 수 있는 APK 설치 화면이 없습니다") }
        catch (_: Exception) { apk.delete(); state("업데이트 확인", true); toast("업데이트 파일을 확인하지 못했습니다. 다시 받아주세요") }
    }
    fun resume() {
        if (failed && !busy.get()) checkForUpdate(automatic = true)
        if (waitingForPermission) {
            waitingForPermission = false
            if (Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()) install()
        }
    }
    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun validUpdate(file: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = activity.packageManager.getPackageInfo(activity.packageName, flags)
        val downloaded = activity.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        if (downloaded.packageName != activity.packageName || version(downloaded) <= version(installed)) return false
        fun signers(info: PackageInfo) = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            ?.map { it.toCharsString() }?.toSet() ?: emptySet()
        val currentSigners = signers(installed)
        return currentSigners.isNotEmpty() && currentSigners == signers(downloaded)
    }
    private fun connect(address: String): HttpURLConnection {
        require(address.startsWith("https://"))
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "GenieBreaker/${BuildConfig.VERSION_NAME}")
        if (address.startsWith("https://api.github.com/")) connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val status = connection.responseCode
            require(status == 200) { "서버 응답 $status" }
            require(connection.url.protocol == "https")
            return connection
        } catch (error: Exception) { connection.disconnect(); throw error }
    }
    private fun toast(text: String) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    private fun ui(action: () -> Unit) { activity.runOnUiThread { if (!activity.isDestroyed && !activity.isFinishing) action() } }
    fun close() { closed = true; handler.removeCallbacksAndMessages(null); executor.shutdownNow() }
}
