package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.*
import android.content.pm.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Android package operations, with immutable snapshots consumed by Flutter. */
internal class AppManagementController(
    private val activity: Activity,
    private val selectStartup: (String, String) -> Unit,
    private val changed: () -> Unit
) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val pm = activity.packageManager
    private val duals = DualApps(activity.applicationContext)
    private var status = "듀얼앱을 만들 원본 앱을 선택하세요"
    private var allApps = false
    private var onlyDuals = false
    private var busy = false
    private var loading = false
    private var packages = emptyList<PackageInfo>()
    private var entries = emptyList<Map<String, Any>>()
    private val catalog = AppCatalog(activity.applicationContext, duals)
    private var refreshAgain = false
    private var pendingInstallPermission: String? = null
    private val installState = SessionInstaller.state(activity)
    private val preferences = activity.getSharedPreferences("apk_workshop", Context.MODE_PRIVATE)
    private fun installStatus() = installState.getString("result", null)?.lineSequence()?.firstOrNull() ?: installState.getString("progress", null) ?: "듀얼앱을 만들 원본 앱을 선택하세요"
    private val installListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> ui {
        status = installStatus()
    } }
    init { installState.registerOnSharedPreferenceChangeListener(installListener) }
    fun snapshot() = mapOf("items" to entries, "status" to status, "busy" to busy, "loading" to loading, "duals" to onlyDuals)
    fun toggleDuals() { if (!loading && !busy) { onlyDuals = !onlyDuals; refresh() } }
    fun viewOptions() = TvMenus.show(activity, "보기 설정", listOf("실행 가능한 앱", "전체 설치 앱"), selected = if (allApps) 1 else 0) { index -> allApps = index == 1; refresh() }
    fun choose(packageName: String) { packages.firstOrNull { it.packageName == packageName }?.let(::choose) }
    fun refresh() {
        if (loading) { refreshAgain = true; return }
        if (busy) return
        loading = true; changed()
        val dualFilter = onlyDuals
        val allFilter = allApps
        worker.execute {
            try {
                val listing = catalog.list(dualFilter, allFilter)
                ui {
                    entries = listing.entries
                    packages = listing.packages
                    status = installStatus()
                    loading = false; changed()
                    if (refreshAgain) { refreshAgain = false; refresh() }
                }
            } catch (error: Exception) { ui { loading = false; status = "앱 목록을 불러오지 못했습니다. [${Diagnostics.record(activity, "App shelf", error)}]" } }
        }
    }
    private fun choose(pkg: PackageInfo) {
        if (busy) return
        val record = duals.record(pkg.packageName) ?: duals.all().firstOrNull { it.target == pkg.packageName }
        val isNetflix = (record?.source ?: pkg.packageName) == NetflixCompatibility.PACKAGE
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        val label = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
        if (isNetflix) actions += "넷플릭스 듀얼앱 상태" to { showNetflixStatus(record) }
        if (record != null && duals.isInstalled(record.target)) {
            actions += "듀얼앱 실행" to { launch(record.target) }
            if (record != null && duals.isInstalled(record.source)) actions += "듀얼앱 다시 만들기" to { create(pm.getPackageInfo(record.source, 0)) }
            actions += "듀얼앱 제거" to { uninstall(record.target) }
        } else if (record != null && duals.file(record).exists()) {
            actions += "듀얼앱 설치" to { install(record.id) }
            if (record != null && duals.isInstalled(record.source)) actions += "듀얼앱 다시 만들기" to { create(pm.getPackageInfo(record.source, 0)) }
        } else actions += "듀얼앱 만들기" to { create(pkg) }
        if (record == null || duals.isInstalled(record.source)) actions += "원본 앱 실행" to { launch(record?.source ?: pkg.packageName) }
        if (launchIntent(activity, pkg.packageName) != null) {
            actions += "시작 앱으로 설정" to { selectStartup(pkg.packageName, label) }
        }
        if (record != null && duals.file(record).exists()) actions += "듀얼앱 APK 저장" to { export(record.id) }
        if (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) == 0) actions += "원본 앱 제거" to { uninstall(record?.source ?: pkg.packageName) }
        actions += "앱 정보" to { notice("${pkg.packageName}\n버전 ${pkg.versionName}") }
        if (!isNetflix) {
            SessionInstaller.resultFor(activity, record?.target ?: pkg.packageName)?.let { result ->
                actions += "설치 결과" to { notice(result) }
            }
        }
        TvMenus.show(activity, label, actions.map { it.first }) { index -> actions.getOrNull(index)?.second?.invoke() }
    }

    private fun installedPackage(packageName: String): PackageInfo? = try {
        pm.getPackageInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun appStatus(pkg: PackageInfo?): String {
        if (pkg == null) return "미설치"
        val application = pkg.applicationInfo
        val launchState = when {
            application == null -> "상태 확인 불가"
            !application.enabled -> "사용 중지됨"
            application.flags and ApplicationInfo.FLAG_SUSPENDED != 0 -> "시스템에서 일시 중지됨"
            launchIntent(activity, pkg.packageName) == null -> "실행 화면 없음"
            else -> "실행 화면 확인됨"
        }
        val version = pkg.versionName?.let { " · 버전 $it" }.orEmpty()
        return "설치됨 · $launchState$version"
    }

    private fun showNetflixStatus(record: DualApp?) {
        val originalPackage = NetflixCompatibility.PACKAGE
        val original = installedPackage(originalPackage)
        val clone = record?.let { installedPackage(it.target) }
        val apkReady = record != null && duals.file(record).isFile
        val result = record?.let { SessionInstaller.resultFor(activity, it.target) }
        val message = listOf(
            "원본 앱 · ${appStatus(original)}",
            "듀얼앱 · ${appStatus(clone)}",
            "듀얼앱 APK · ${if (apkReady) "준비됨" else "없음"}",
            "최근 설치 결과 · ${result?.lineSequence()?.firstOrNull() ?: "기록 없음"}",
            "‘실행 화면 확인됨’은 Android에서 앱의 실행 화면을 찾았다는 의미입니다."
        ).joinToString("\n")
        val actions = mutableListOf<Pair<String, () -> Unit>>("닫기" to {})
        if (original != null) {
            actions += "원본 앱 설정" to {
                if (!openAppDetails(activity, originalPackage)) notice("TV의 앱 목록에서 원본 앱의 정보를 여세요.")
            }
        }
        if (result != null) actions += "설치 결과 상세 정보" to { notice(result) }
        TvMenus.show(activity, "넷플릭스 듀얼앱 상태", actions.map { it.first }, message) { index ->
            actions[index].second()
        }
    }

    private fun create(pkg: PackageInfo) {
        if (busy) return
        val netflix = pkg.packageName == NetflixCompatibility.PACKAGE
        val supported = netflix
        val defaults = CloneOptions(netflixCompatibility = supported).values().filterKeys { netflix || it != "netflixCompatibility" }
        val saved = preferences.getString("options_${pkg.packageName}", null)
        val values = try { val json = JSONObject(saved ?: "{}"); defaults.mapValues { (key, fallback) -> json.optBoolean(key, fallback) } } catch (_: Exception) { defaults }
        val label = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: "듀얼앱"
        val description = if (!netflix) label else "$label · ${pkg.versionName}\n설치된 넷플릭스의 실행 코드를 검사합니다. 원본 앱은 설치한 상태에서 사용 설정을 유지하세요."
        TvMenus.cloneOptions(activity, description, values) { chosen ->
            preferences.edit().putString("options_${pkg.packageName}", JSONObject(chosen).toString()).apply()
            createWithOptions(pkg, CloneOptions.from(chosen))
        }
    }
    private fun createWithOptions(pkg: PackageInfo, options: CloneOptions) {
        if (busy) return
        if (Build.VERSION.SDK_INT < 26) { notice("이 기기에서는 듀얼앱을 만들 수 없습니다."); return }
        busy = true; status = "듀얼앱 생성 중"; changed()
        worker.execute {
            try {
                val record = duals.build(pkg, options) { progress -> ui { status = progress } }
                ui { busy = false; install(record.id) }
            } catch (error: Throwable) {
                val code = Diagnostics.record(activity, "Dual app build", error)
                ui { busy = false; status = "듀얼앱을 만들지 못했습니다"; notice("${error.message ?: "듀얼앱을 만들지 못했습니다"} [$code]") }
            }
        }
    }
    private fun launch(packageName: String) {
        try {
            Diagnostics.record(activity, "Launch requested: $packageName")
            val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                Diagnostics.record(activity, "Launch failed", step = DiagnosticStep.LAUNCH,
                    cause = DiagnosticCause.LAUNCH_TARGET_MISSING)
                notice("실행 화면이 없는 앱입니다.")
            } else activity.startActivity(intent)
        } catch (error: Exception) {
            val cause = when (error) {
                is SecurityException -> DiagnosticCause.SYSTEM_PERMISSION
                is android.content.ActivityNotFoundException -> DiagnosticCause.LAUNCH_TARGET_MISSING
                else -> DiagnosticCause.ERROR_UNSPECIFIED
            }
            val code = Diagnostics.record(activity, "Launch", error, DiagnosticStep.LAUNCH, cause = cause)
            notice("앱을 실행할 수 없습니다. [$code]")
        }
    }
    private fun uninstall(packageName: String) {
        try {
            val app = pm.getApplicationInfo(packageName, 0)
            if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) { notice("시스템 앱은 일반 권한으로 제거할 수 없습니다."); return }
            TvMenus.show(activity, "앱 제거 확인", listOf("취소", "제거 요청"), "${app.loadLabel(pm)}\n앱을 제거하면 저장된 데이터도 삭제됩니다.") { choice ->
                if (choice == 1) try { activity.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))) } catch (error: Exception) { notice("제거 요청 실패: ${error.message}") }
            }
        } catch (error: Exception) { notice("앱 정보를 읽지 못했습니다: ${error.message}") }
    }
    private fun install(id: String) {
        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
            Diagnostics.record(activity, "Install blocked", step = DiagnosticStep.INSTALL,
                cause = DiagnosticCause.INSTALL_PERMISSION)
            TvMenus.show(activity, "설치 권한 필요", listOf("취소", "허용 화면"), "이 앱의 ‘알 수 없는 앱 설치’를 허용해야 합니다.") { choice ->
                if (choice != 1) return@show
                try {
                    pendingInstallPermission = id
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", activity.packageName, null))
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    pendingInstallPermission = null
                    notice("TV 설정에서 GenieBreaker의 ‘알 수 없는 앱 설치’를 허용한 뒤 다시 시도하세요.")
                }
            }
            return
        }
        try {
            val file = File(activity.filesDir, "edited/$id.apk")
            require(file.isFile)
            val metadata = JSONObject(File(activity.filesDir, "edited/$id.json").readText())
            SessionInstaller.submit(activity, file, metadata.getString("package"))
            status = "TV의 설치 확인 대기 중"
        } catch (error: Exception) { notice("설치 화면을 열지 못했습니다: ${error.message}") }
    }
    fun resume() {
        val id = pendingInstallPermission
        pendingInstallPermission = null
        if (id != null && pm.canRequestPackageInstalls()) install(id)
        refresh()
    }
    private fun export(id: String) {
        preferences.edit().putString("pending_export", id).apply()
        try { activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).putExtra(Intent.EXTRA_LOCAL_ONLY, true).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE, "GenieBreaker-$id.apk"), 610) }
        catch (error: Exception) { notice("파일 저장 화면이 없습니다: ${error.message}") }
    }
    fun exportResult(result: Int, data: Intent?) {
        val id = preferences.getString("pending_export", null); preferences.edit().remove("pending_export").apply()
        val uri = data?.data
        if (result != Activity.RESULT_OK || id == null || uri == null) return
        if (!FileController.isLocalDocument(uri)) {
            notice("내부 저장소나 USB의 로컬 폴더에만 저장할 수 있습니다. [GB-UI-0001]"); return
        }
        worker.execute { FileWork.reading { try {
            activity.contentResolver.openOutputStream(uri)?.use { output -> File(activity.filesDir, "edited/$id.apk").inputStream().use { it.copyTo(output) } } ?: error("저장 위치를 열지 못했습니다")
            ui { notice("APK를 선택한 폴더에 저장했습니다") }
        } catch (error: Exception) { ui { notice("저장 실패: ${error.message}") } } } }
    }
    private fun notice(message: String) { if (!activity.isDestroyed && !activity.isFinishing) TvMenus.notice(activity, message) }
    private fun ui(action: () -> Unit) { main.post { if (!activity.isDestroyed && !activity.isFinishing) { action(); changed() } } }
    fun close() { installState.unregisterOnSharedPreferenceChangeListener(installListener); worker.shutdownNow(); catalog.clear(); main.removeCallbacksAndMessages(null) }
}
