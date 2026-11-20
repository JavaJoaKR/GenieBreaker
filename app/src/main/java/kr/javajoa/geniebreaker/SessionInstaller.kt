package kr.javajoa.geniebreaker

import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.util.concurrent.Executors

/** Preserve the platform's actual result instead of relying on generic installer error screens. */
internal object SessionInstaller {
    const val RESULT_ACTION = "kr.javajoa.geniebreaker.INSTALL_RESULT"
    fun state(context: Context) = context.getSharedPreferences("install_results", Context.MODE_PRIVATE)

    fun resultFor(context: Context, packageName: String): String? {
        val results = state(context)
        return results.getString("result:$packageName", null)
            ?: results.getString("result", null)?.takeIf {
                it.lineSequence().drop(1).firstOrNull() == "대상: $packageName"
            }
    }

    fun recordResult(context: Context, packageName: String, message: String) {
        state(context).edit().remove("progress")
            .putString("result", message)
            .putString("result:$packageName", message)
            .apply()
    }

    fun submit(context: Context, apk: File, expectedPackage: String, deleteAfterStaging: Boolean = false) {
        val app = context.applicationContext
        state(app).edit().putString("progress", "설치 파일 준비 중").remove("result").apply()
        val worker = Executors.newSingleThreadExecutor()
        FileWork.lock.readLock().lock()
        try { FileWork.pending.incrementAndGet() } finally { FileWork.lock.readLock().unlock() }
        worker.execute {
            FileWork.lock.readLock().lock()
            var id = -1
            var committed = false
            try {
                val parsed = app.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: error("APK를 읽을 수 없습니다")
                require(parsed.packageName == expectedPackage) { "설치 대상 불일치: 요청 $expectedPackage / 실제 ${parsed.packageName}" }
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(expectedPackage); setSize(apk.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val installer = app.packageManager.packageInstaller
                id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { target ->
                        apk.inputStream().use { source -> source.copyTo(target) }; session.fsync(target)
                    }
                    val callback = Intent(app, InstallResultReceiver::class.java).setAction(RESULT_ACTION)
                        .setData(android.net.Uri.parse("geniebreaker://install/$id"))
                        .putExtra("expected_package", expectedPackage)
                    val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val pending = PendingIntent.getBroadcast(app, id, callback, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                    state(app).edit().putString("progress", "TV의 설치 확인 대기 중").apply()
                    session.commit(pending.intentSender); committed = true
                }
            } catch (error: Exception) {
                val code = Diagnostics.record(app, "Install", error)
                recordResult(app, expectedPackage, "설치 요청 실패 [$code]\n대상: $expectedPackage\n${error.javaClass.simpleName}: ${error.message}")
            } finally {
                if (id >= 0 && !committed) try { app.packageManager.packageInstaller.abandonSession(id) } catch (_: Exception) { }
                if (deleteAfterStaging) runCatching { apk.delete() }
                FileWork.pending.decrementAndGet()
                FileWork.lock.readLock().unlock()
                worker.shutdown()
            }
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SessionInstaller.RESULT_ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val expected = intent.getStringExtra("expected_package") ?: "알 수 없음"
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            try {
                requireNotNull(confirmation) { "설치 확인 화면을 받지 못했습니다" }
                context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (error: Exception) {
                val code = Diagnostics.record(context, "Installer confirmation", error)
                SessionInstaller.recordResult(context, expected, "설치 확인 화면을 열지 못했습니다 [$code]\n대상: $expected\n${error.message}")
            }
            return
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "시스템에서 상세 메시지를 반환하지 않았습니다."
        val other = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
        val legacy = intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS", 0)
        val explanation = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "설치 완료"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "이미 설치된 앱과 충돌하여 설치를 완료하지 못했습니다."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "TV가 설치를 차단했습니다."
            PackageInstaller.STATUS_FAILURE_ABORTED -> "설치가 취소되었습니다."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "설치에 필요한 저장 공간이 부족합니다."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK가 이 기기의 설치 요건과 일치하지 않습니다."
            else -> "설치 실패"
        }
        val result = "$explanation\n대상: $expected\n상태: $status / $legacy\n${if (other != null) "충돌 앱: $other\n" else ""}$message"
        val code = Diagnostics.record(context, "Install", if (status == PackageInstaller.STATUS_SUCCESS) null else IllegalStateException())
        SessionInstaller.recordResult(context, expected, if (status == PackageInstaller.STATUS_SUCCESS) result else "$result\n[$code]")
    }
}
