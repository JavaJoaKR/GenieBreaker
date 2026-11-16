package kr.javajoa.geniebreaker

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** A remote-first app shelf: choose an app, create a second copy, confirm installation. */
internal class AppManagementView(private val activity: Activity) : LinearLayout(activity) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val pm = activity.packageManager
    private val duals = DualApps(activity.applicationContext)
    private val grid = GridView(activity)
    private val status = text("앱을 선택해 듀얼앱을 만드세요", 14)
    private val empty = text("표시할 앱이 없습니다", 18)
    private var allApps = false
    private var onlyDuals = false
    private var busy = false
    private var loading = false
    private var packages = emptyList<PackageInfo>()
    private val installState = SessionInstaller.state(activity)
    private val preferences = activity.getSharedPreferences("apk_workshop", Context.MODE_PRIVATE)
    private val installListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> main.post {
        if (!activity.isDestroyed) {
            status.text = installState.getString("result", null)?.lineSequence()?.firstOrNull() ?: installState.getString("progress", null) ?: "앱을 선택해 듀얼앱을 만드세요"
            grid.invalidateViews()
        }
    } }
    init {
        orientation = VERTICAL
        installState.registerOnSharedPreferenceChangeListener(installListener)
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("앱 관리", 32, true), LayoutParams(0, -2, 1f))
        val dualFilter = button("내 듀얼앱") { }
        dualFilter.setOnClickListener { onlyDuals = !onlyDuals; dualFilter.text = if (onlyDuals) "모든 앱" else "내 듀얼앱"; refresh() }
        header.addView(dualFilter, LayoutParams(dp(112), dp(44)))
        val more = button("보기 설정") { AlertDialog.Builder(activity).setTitle("앱 표시").setSingleChoiceItems(arrayOf("실행 가능한 앱", "전체 설치 앱"), if (allApps) 1 else 0) { dialog, index -> allApps = index == 1; dialog.dismiss(); refresh() }.setNegativeButton("닫기", null).show() }
        header.addView(more, LayoutParams(dp(102), dp(44)).apply { leftMargin = dp(10) })
        addView(header)
        status.maxLines = 1; status.ellipsize = android.text.TextUtils.TruncateAt.END
        status.isFocusable = true; status.background = focusBackground()
        status.setPadding(dp(12), dp(10), dp(12), dp(10))
        status.setOnClickListener { notice(installState.getString("result", null) ?: installState.getString("progress", null) ?: "앱을 선택하고 ‘듀얼앱 만들기’를 누르세요. 설치 확인만 하면 별도 앱으로 사용할 수 있습니다.") }
        addView(status, LayoutParams(-1, dp(44)).apply { topMargin = dp(18); bottomMargin = dp(18) })
        val shelf = FrameLayout(activity)
        addView(shelf, LayoutParams(-1, 0, 1f))
        grid.numColumns = GridView.AUTO_FIT; grid.columnWidth = dp(200); grid.horizontalSpacing = dp(14); grid.verticalSpacing = dp(14)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH; grid.clipToPadding = false; grid.setPadding(dp(3), dp(3), dp(3), dp(3))
        if (Build.VERSION.SDK_INT >= 26) grid.setDefaultFocusHighlightEnabled(false)
        grid.selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        shelf.addView(grid, FrameLayout.LayoutParams(-1, -1))
        shelf.addView(empty.apply { gravity = Gravity.CENTER; visibility = View.GONE }, FrameLayout.LayoutParams(-1, -1))
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, index, _ -> choose(packages[index]) }
        addView(text("방향키로 이동  ·  확인으로 선택", 11), LayoutParams(-1, dp(28)).apply { topMargin = dp(14) })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Int, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(if (bold) 0xffeff4ff.toInt() else 0xff9baac1.toInt())
        includeFontPadding = false; if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun shape(color: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat(); setStroke(dp(2), stroke) }
    private fun focusBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), shape(0xff203c62.toInt(), 0xff7aafff.toInt()))
        addState(intArrayOf(android.R.attr.state_focused), shape(0xff203c62.toInt(), 0xff7aafff.toInt()))
        addState(intArrayOf(android.R.attr.state_pressed), shape(0xff203c62.toInt(), 0xff7aafff.toInt()))
        addState(intArrayOf(), shape(0xff131f33.toInt()))
    }
    private fun button(title: String, action: () -> Unit) = Button(activity).apply {
        text = title; textSize = 13f; isAllCaps = false; isFocusable = true; stateListAnimator = null
        if (Build.VERSION.SDK_INT >= 26) setDefaultFocusHighlightEnabled(false)
        background = focusBackground(); setTextColor(0xffeff4ff.toInt()); setPadding(dp(10), 0, dp(10), 0); setOnClickListener { action() }
    }
    private val adapter: BaseAdapter get() = object : BaseAdapter() {
        override fun getCount() = packages.size
        override fun getItem(position: Int) = packages[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val card = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(152)
                setPadding(dp(18), dp(20), dp(18), dp(18)); background = focusBackground()
                addView(ImageView(activity), LayoutParams(dp(48), dp(48)))
                addView(text("", 17, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER }, LayoutParams(-1, -2).apply { topMargin = dp(15) })
                addView(text("", 12).apply { gravity = Gravity.CENTER }, LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
            val pkg = packages[position]; val app = pkg.applicationInfo
            (card.getChildAt(0) as ImageView).setImageDrawable(app?.loadIcon(pm))
            (card.getChildAt(1) as TextView).text = app?.loadLabel(pm) ?: "앱"
            val record = duals.record(pkg.packageName) ?: duals.all().firstOrNull { it.target == pkg.packageName }
            (card.getChildAt(2) as TextView).text = when { record == null -> "듀얼앱 만들기"; duals.isInstalled(record.target) -> "듀얼앱 사용 중"; else -> "설치 준비됨" }
            return card
        }
    }
    fun refresh() {
        if (loading || busy) return
        loading = true
        worker.execute {
            try {
                val records = duals.all(); val clonePackages = records.map { it.target }.toSet()
                val items = if (onlyDuals) records.mapNotNull { record ->
                    val installed = try { pm.getPackageInfo(record.source, 0) } catch (_: PackageManager.NameNotFoundException) {
                        try { pm.getPackageInfo(record.target, 0) } catch (_: PackageManager.NameNotFoundException) { null }
                    }
                    installed ?: pm.getPackageArchiveInfo(duals.file(record).absolutePath, 0)?.apply {
                        applicationInfo?.sourceDir = duals.file(record).absolutePath
                        applicationInfo?.publicSourceDir = duals.file(record).absolutePath
                    }
                }.sortedBy { it.applicationInfo?.loadLabel(pm).toString().lowercase() }
                else pm.getInstalledPackages(0).filter { pkg ->
                    pkg.packageName != activity.packageName && pkg.packageName !in clonePackages && pkg.applicationInfo != null &&
                        (allApps || pm.getLeanbackLaunchIntentForPackage(pkg.packageName) != null || pm.getLaunchIntentForPackage(pkg.packageName) != null)
                }.sortedBy { it.applicationInfo!!.loadLabel(pm).toString().lowercase() }
                ui {
                    packages = items; grid.adapter = adapter; empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    empty.text = if (onlyDuals) "아직 만든 듀얼앱이 없습니다" else "표시할 앱이 없습니다. 보기 설정을 확인하세요."
                    status.text = installState.getString("result", null)?.lineSequence()?.firstOrNull() ?: installState.getString("progress", null) ?: "앱을 선택해 듀얼앱을 만드세요"
                    loading = false
                }
            } catch (error: Exception) { ui { loading = false; status.text = "앱을 불러오지 못했습니다. 다시 시도하세요."; Diagnostics.record(activity, "App shelf", error) } }
        }
    }
    private fun choose(pkg: PackageInfo) {
        if (busy) return
        val record = duals.record(pkg.packageName) ?: duals.all().firstOrNull { it.target == pkg.packageName }
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (record != null && duals.isInstalled(record.target)) {
            actions += "듀얼앱 실행" to { launch(record.target) }
            if (record != null && duals.isInstalled(record.source)) actions += "듀얼앱 다시 만들기" to { create(pm.getPackageInfo(record.source, 0)) }
            actions += "듀얼앱 제거" to { uninstall(record.target) }
        } else if (record != null && duals.file(record).exists()) {
            actions += "듀얼앱 설치" to { install(record.id) }
            if (record != null && duals.isInstalled(record.source)) actions += "듀얼앱 다시 만들기" to { create(pm.getPackageInfo(record.source, 0)) }
        } else actions += "듀얼앱 만들기" to { create(pkg) }
        if (record == null || duals.isInstalled(record.source)) actions += "원본 앱 실행" to { launch(record?.source ?: pkg.packageName) }
        actions += "더 보기" to { more(pkg, record) }
        AlertDialog.Builder(activity).setTitle(pkg.applicationInfo?.loadLabel(pm)).setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }.setNegativeButton("닫기", null).show()
    }
    private fun create(pkg: PackageInfo) {
        if (busy) return
        if (Build.VERSION.SDK_INT < 26) { notice("이 기기에서는 듀얼앱을 만들 수 없습니다."); return }
        busy = true; grid.isEnabled = false; status.text = "듀얼앱을 준비하고 있습니다"
        worker.execute {
            try {
                val record = duals.build(pkg) { progress -> ui { status.text = progress } }
                ui { busy = false; grid.isEnabled = true; grid.invalidateViews(); install(record.id) }
            } catch (error: Throwable) {
                Diagnostics.record(activity, "Dual app build", error)
                ui { busy = false; grid.isEnabled = true; status.text = "듀얼앱을 만들지 못했습니다"; notice("이 앱의 듀얼앱을 만들지 못했습니다. 설정의 문제 기록에서 원인을 확인할 수 있습니다.") }
            }
        }
    }
    private fun launch(packageName: String) {
        try {
            val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
            if (intent != null) activity.startActivity(intent) else notice("실행 화면이 없는 앱입니다.")
        } catch (error: Exception) { Diagnostics.record(activity, "Launch", error); notice("앱을 실행할 수 없습니다. 오류 기록을 확인하세요.") }
    }
    private fun more(pkg: PackageInfo, record: DualApp?) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (record != null && duals.file(record).exists()) actions += "듀얼앱 APK 저장" to { export(record.id) }
        if (record == null || duals.isInstalled(record.source)) actions += "원본 앱 제거" to { uninstall(record?.source ?: pkg.packageName) }
        actions += "설치 결과 보기" to { notice(installState.getString("result", null) ?: "설치 결과가 없습니다") }
        actions += "앱 정보" to { notice("${pkg.packageName}\n버전 ${pkg.versionName}\n${record?.let { "듀얼앱: ${it.target}" } ?: ""}") }
        AlertDialog.Builder(activity).setTitle("앱 관리").setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }.setNegativeButton("닫기", null).show()
    }
    private fun uninstall(packageName: String) {
        try {
            val app = pm.getApplicationInfo(packageName, 0)
            if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) { notice("시스템 앱은 일반 권한으로 제거할 수 없습니다."); return }
            AlertDialog.Builder(activity).setTitle("앱을 제거할까요?").setMessage("${app.loadLabel(pm)}\n이 앱의 데이터도 함께 삭제됩니다.")
                .setPositiveButton("제거 요청") { _, _ -> try { activity.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))) } catch (error: Exception) { notice("제거 요청 실패: ${error.message}") } }
                .setNegativeButton("취소", null).show()
        } catch (error: Exception) { notice("앱 정보를 읽지 못했습니다: ${error.message}") }
    }
    private fun install(id: String) {
        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
            preferences.edit().putString("pending_install", id).apply()
            AlertDialog.Builder(activity).setTitle("설치 권한 필요").setMessage("이 앱의 ‘알 수 없는 앱 설치’를 허용해야 합니다.")
                .setPositiveButton("허용 화면") { _, _ -> try { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) } catch (error: Exception) { notice(error.message ?: "허용 화면 없음") } }
                .setNegativeButton("취소") { _, _ -> preferences.edit().remove("pending_install").apply() }.show()
            return
        }
        preferences.edit().remove("pending_install").apply()
        try {
            val file = File(activity.filesDir, "edited/$id.apk")
            require(file.isFile)
            val metadata = JSONObject(File(activity.filesDir, "edited/$id.json").readText())
            SessionInstaller.submit(activity, file, metadata.getString("package"))
            status.text = "설치 확인을 기다리고 있습니다"
        } catch (error: Exception) { notice("설치 화면을 열지 못했습니다: ${error.message}") }
    }
    fun resume() {
        preferences.getString("pending_install", null)?.let { id -> if (Build.VERSION.SDK_INT < 26 || pm.canRequestPackageInstalls()) install(id) }
        refresh()
    }
    private fun export(id: String) {
        preferences.edit().putString("pending_export", id).apply()
        try { activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE, "GenieBreaker-$id.apk"), 610) }
        catch (error: Exception) { notice("파일 저장 화면이 없습니다: ${error.message}") }
    }
    fun exportResult(result: Int, data: Intent?) {
        val id = preferences.getString("pending_export", null); preferences.edit().remove("pending_export").apply()
        val uri = data?.data
        if (result != Activity.RESULT_OK || id == null || uri == null) return
        worker.execute { try {
            activity.contentResolver.openOutputStream(uri)?.use { output -> File(activity.filesDir, "edited/$id.apk").inputStream().use { it.copyTo(output) } } ?: error("저장 위치를 열지 못했습니다")
            ui { notice("APK를 저장했습니다") }
        } catch (error: Exception) { ui { notice("저장 실패: ${error.message}") } } }
    }
    private fun notice(message: String) { if (!activity.isDestroyed && !activity.isFinishing) AlertDialog.Builder(activity).setMessage(message).setPositiveButton("닫기", null).show() }
    private fun ui(action: () -> Unit) { main.post { if (!activity.isDestroyed && !activity.isFinishing) action() } }
    fun close() { installState.unregisterOnSharedPreferenceChangeListener(installListener); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
