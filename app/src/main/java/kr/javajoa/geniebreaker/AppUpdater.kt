package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val CHECK_COOLDOWN_MS = 5_000L

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
    @Volatile private var nextCheckAt = 0L
    @Volatile private var failed = false
    @Volatile private var closed = false
    private val retry = Runnable { if (!closed) checkForUpdate(automatic = true) }
    private fun cooldownRemaining() = (nextCheckAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    private fun succeeded() { failed = false; retryCount = 0; handler.removeCallbacks(retry) }
    private fun scheduleRetry() {
        if (closed || retryCount >= retryDelays.size) return
        val delay = maxOf(retryDelays[retryCount++], cooldownRemaining())
        handler.removeCallbacks(retry); handler.postDelayed(retry, delay)
    }

    fun checkForUpdate(automatic: Boolean = false) {
        if (closed) return
        if (!busy.compareAndSet(false, true)) return
        val remaining = cooldownRemaining()
        if (remaining > 0) {
            busy.set(false)
            if (!automatic) toast("${(remaining + 999) / 1000}초 뒤에 다시 확인할 수 있습니다")
            return
        }
        handler.removeCallbacks(retry)
        ui { state("업데이트 확인 중", false) }
        executor.execute {
            try { StorageTools.pruneStaging(activity) } catch (error: Exception) { Diagnostics.record(activity, "APK staging cleanup", error) }
            FileWork.lock.readLock().lock()
            var cachedVersion = 0L
            try {
                if (apk.exists()) {
                    if (validUpdate(apk)) {
                        cachedVersion = version(requireNotNull(activity.packageManager.getPackageArchiveInfo(apk.path, 0)))
                        ui { state("업데이트 설치", true) }
                    } else apk.delete()
                }
                // A cached APK must be detected before applying the successful-check interval.
                val checkAge = System.currentTimeMillis() - preferences.getLong("last_check", 0)
                if (automatic && !failed && checkAge in 0 until 6L * 60 * 60 * 1000) {
                    ui { state(if (cachedVersion > 0) "업데이트 설치" else "업데이트 확인", true) }; return@execute
                }
                nextCheckAt = SystemClock.elapsedRealtime() + CHECK_COOLDOWN_MS
                val releases = catalogLoader?.invoke() ?: run {
                    val connection = connect("https://api.github.com/repos/JavaJoaKR/GenieBreaker/releases?per_page=100")
                    try { connection.inputStream.bufferedReader().use { JSONArray(it.readText()) } }
                    finally { connection.disconnect() }
                }
                val installed = version(activity.packageManager.getPackageInfo(activity.packageName, 0))
                val latest = ReleaseCatalog.latest(releases)
                val candidate = latest.url
                if (latest.version <= maxOf(installed, cachedVersion)) {
                    succeeded()
                    preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    ui { state(if (cachedVersion > 0) "업데이트 설치" else "업데이트 확인", true); if (!automatic && cachedVersion == 0L) toast("최신 버전입니다") }
                    return@execute
                }
                ui { state("업데이트 다운로드 중", false) }
                apk.parentFile?.mkdirs()
                val temporary = File.createTempFile("download-", ".apk", apk.parentFile)
                try {
                    val download = connect(candidate)
                    try {
                        require(download.contentLength.toLong() <= 128L * 1024 * 1024) { "업데이트 파일이 128MB 한도를 초과합니다" }
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
                    require(validUpdate(temporary) && activity.packageManager.getPackageArchiveInfo(temporary.path, 0)?.let(::version) == latest.version) { "다운로드한 APK의 앱 ID, 버전 또는 서명이 예상과 다릅니다" }
                    check(temporary.renameTo(apk)) { "업데이트 파일을 저장하지 못했습니다" }
                    succeeded()
                    preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    ui { state("업데이트 설치", true); if (!automatic) toast("다운로드를 완료했습니다. ‘업데이트 설치’를 선택하세요.") }
                } finally { temporary.delete() }
            } catch (error: Exception) {
                if (!closed) {
                    failed = true
                    val code = Diagnostics.record(activity, "Update check", error)
                    ui { state(if (cachedVersion > 0) "업데이트 설치" else "업데이트 재시도 [$code]", true); if (!automatic) toast("확인하지 못했습니다. [$code]") }
                }
            } finally {
                busy.set(false); FileWork.lock.readLock().unlock()
                if (failed && !closed) scheduleRetry()
            }
        }
    }
    fun actionLabel(fallback: String) = if (busy.get()) fallback else if (apk.exists()) "업데이트 설치" else "업데이트 확인"
    fun click() {
        if (busy.get()) return
        if (apk.exists()) install() else checkForUpdate()
    }
    private fun install() {
        try {
            require(validUpdate(apk))
            if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                TvMenus.show(activity, "앱 업데이트 허용", listOf("나중에", "허용 화면 열기"), "업데이트를 설치하려면 TV 설정에서 GenieBreaker의 ‘알 수 없는 앱 설치’를 허용하세요.") { choice ->
                    if (choice == 1) try {
                        waitingForPermission = true
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    } catch (_: Exception) { waitingForPermission = false; toast("TV 설정에서 ‘알 수 없는 앱 설치’ 권한을 확인하세요") }
                }
                return
            }
            SessionInstaller.submit(activity, apk, activity.packageName)
        } catch (_: ActivityNotFoundException) { toast("이 TV에서 APK 설치 화면을 열 수 없습니다") }
        catch (error: Exception) { val code = Diagnostics.record(activity, "Update install", error); apk.delete(); state("업데이트 확인", true); toast("업데이트 파일 확인 실패 [$code]") }
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
