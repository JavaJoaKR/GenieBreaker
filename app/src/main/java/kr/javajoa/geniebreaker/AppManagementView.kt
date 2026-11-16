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
import android.text.InputType
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

internal class AppManagementView(private val activity: Activity) : LinearLayout(activity) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val pm = activity.packageManager
    private val list = ListView(activity)
    private val status = text("설치된 앱을 불러오는 중", 13)
    private var allApps = false
    private var busy = false
    private var loading = false
    private var packages = emptyList<PackageInfo>()
    private val installState = SessionInstaller.state(activity)
    private val installListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.post { if (!activity.isDestroyed) {
            status.text = installState.getString("result", null) ?: installState.getString("progress", null) ?: status.text
        } }
    }
    private val preferences = activity.getSharedPreferences("apk_workshop", Context.MODE_PRIVATE)
    init {
        installState.registerOnSharedPreferenceChangeListener(installListener)
        status.isFocusable = true; status.setOnClickListener {
            notice(installState.getString("result", null) ?: installState.getString("progress", null) ?: "설치 결과가 없습니다")
        }
        status.maxLines = 2; status.ellipsize = android.text.TextUtils.TruncateAt.END
        orientation = VERTICAL
        val top = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("앱 관리", 32, true), LayoutParams(0, -2, 1f))
        val filter = button("전체 앱 보기") { }
        filter.setOnClickListener { allApps = !allApps; filter.text = if (allApps) "사용자 앱만" else "전체 앱 보기"; refresh() }
        top.addView(filter, LayoutParams(dp(132), dp(46)))
        top.addView(button("수정본") { showOutputs() }, LayoutParams(dp(94), dp(46)).apply { leftMargin = dp(10) })
        addView(top)
        addView(status, LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        list.divider = null; list.selector = Color.TRANSPARENT.let { android.graphics.drawable.ColorDrawable(it) }
        list.setPadding(0, 0, dp(4), 0); list.clipToPadding = false
        list.adapter = adapter
        list.setOnItemClickListener { _, _, index, _ -> details(packages[index]) }
        addView(list, LayoutParams(-1, 0, 1f))
        addView(text("방향키로 이동     확인으로 관리 메뉴 열기", 12), LayoutParams(-1, dp(30)).apply { topMargin = dp(12) })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Int, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(if (bold) 0xfff0f3f9.toInt() else 0xffa6afbe.toInt())
        includeFontPadding = false; if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun shape(color: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat(); setStroke(dp(2), stroke) }
    private fun focusBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), shape(0xff303e52.toInt(), 0xffa4c8ff.toInt()))
        addState(intArrayOf(android.R.attr.state_focused), shape(0xff303e52.toInt(), 0xffa4c8ff.toInt()))
        addState(intArrayOf(android.R.attr.state_pressed), shape(0xff303e52.toInt(), 0xffa4c8ff.toInt()))
        addState(intArrayOf(), shape(0xff20252e.toInt()))
    }
    private fun button(title: String, action: () -> Unit) = Button(activity).apply {
        text = title; textSize = 13f; isAllCaps = false; isFocusable = true
        background = focusBackground(); setTextColor(0xfff0f3f9.toInt()); setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { action() }
    }
    private val adapter: BaseAdapter get() = object : BaseAdapter() {
        override fun getCount() = packages.size
        override fun getItem(position: Int) = packages[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL; orientation = HORIZONTAL
                setPadding(dp(20), dp(16), dp(20), dp(16)); minimumHeight = dp(86); background = focusBackground()
                addView(ImageView(activity), LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(20) })
                addView(LinearLayout(activity).apply { orientation = VERTICAL
                    addView(text("", 18, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    addView(text("", 12).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(7), 0, 0) })
                }, LayoutParams(0, -2, 1f))
                addView(text("", 12))
            }
            val pkg = packages[position]; val app = pkg.applicationInfo
            (row.getChildAt(0) as ImageView).setImageDrawable(app?.loadIcon(pm))
            val lines = row.getChildAt(1) as LinearLayout
            (lines.getChildAt(0) as TextView).text = app?.loadLabel(pm) ?: pkg.packageName
            (lines.getChildAt(1) as TextView).text = pkg.packageName
            (row.getChildAt(2) as TextView).text = if ((app?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0) "시스템" else "설치됨"
            return row
        }
    }
    @Suppress("DEPRECATION")
    fun refresh() {
        if (loading || busy) return
        loading = true
        worker.execute {
            try {
                val items = pm.getInstalledPackages(0).filter { pkg -> pkg.packageName != activity.packageName && pkg.applicationInfo != null &&
                    (allApps || (pkg.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM == 0)) }
                    .sortedBy { it.applicationInfo!!.loadLabel(pm).toString().lowercase() }
                ui { packages = items; list.adapter = adapter; status.text = installState.getString("result", null) ?: installState.getString("progress", null) ?: "${items.size}개 앱  ·  선택하면 실행·편집·제거할 수 있습니다."; loading = false }
            } catch (error: Exception) { ui { loading = false; status.text = "앱 목록을 읽지 못했습니다: ${error.message}" } }
        }
    }
    private fun details(pkg: PackageInfo) {
        val app = pkg.applicationInfo ?: return
        AlertDialog.Builder(activity).setTitle(app.loadLabel(pm))
            .setItems(arrayOf("앱 실행", "APK 수정 · 재서명 · 설치", "앱 제거", "패키지 정보")) { _, index ->
                when (index) {
                    0 -> try {
                        val intent = pm.getLeanbackLaunchIntentForPackage(pkg.packageName) ?: pm.getLaunchIntentForPackage(pkg.packageName)
                        if (intent != null) activity.startActivity(intent) else notice("실행 화면이 없는 앱입니다.")
                    } catch (error: Exception) { notice("실행 실패: ${error.message}") }
                    1 -> edit(pkg)
                    2 -> uninstall(pkg.packageName)
                    else -> notice("${pkg.packageName}\n버전 ${pkg.versionName}\nAPK ${1 + (app.splitSourceDirs?.size ?: 0)}개\n${app.sourceDir}")
                }
            }.setNegativeButton("닫기", null).show()
    }
    @Suppress("DEPRECATION")
    private fun edit(pkg: PackageInfo) {
        if (busy) { notice("다른 APK를 처리 중입니다."); return }
        if (Build.VERSION.SDK_INT < 26) { notice("APK 편집은 Android 8 이상에서 지원합니다."); return }
        val app = pkg.applicationInfo ?: return
        val fields = LinearLayout(activity).apply { orientation = VERTICAL; setPadding(dp(24), dp(10), dp(24), dp(10)) }
        fun field(title: String, value: String, number: Boolean = false): EditText {
            fields.addView(text(title, 12))
            return EditText(activity).apply {
                setText(value); setSingleLine(); textSize = 16f
                inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                fields.addView(this, LayoutParams(-1, dp(50)))
            }
        }
        val label = field("앱 이름", app.loadLabel(pm).toString())
        val packageName = field("패키지명", availablePackage(pkg.packageName))
        val version = field("버전 코드", pkg.versionCode.toString(), true)
        val fresh = CheckBox(activity).apply { text = "새 서명 키 생성 (체크 해제 시 기존 수정본과 같은 키)"; textSize = 12f }
        fields.addView(fresh)
        fields.addView(text("원본은 유지됩니다. 수정본은 새 서명으로 생성됩니다. 패키지 변경 후 DRM·로그인·푸시 기능이 동작하지 않을 수 있습니다.", 12).apply { setPadding(0, dp(10), 0, 0) })
        val dialog = AlertDialog.Builder(activity).setTitle("APK 수정")
            .setView(ScrollView(activity).apply { addView(fields) }).setPositiveButton("수정본 생성", null).setNegativeButton("취소", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = version.text.toString().toIntOrNull()
            if (code == null || code < 1) { version.error = "1 이상의 숫자를 입력하세요"; return@setOnClickListener }
            val spec = ApkEditSpec(label.text.toString().trim(), packageName.text.toString().trim(), code, fresh.isChecked)
            if (!spec.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { packageName.error = "올바른 패키지명을 입력하세요"; return@setOnClickListener }
            if (spec.freshKey && installed(spec.packageName)) {
                packageName.error = "이미 설치된 패키지입니다. 새 서명에는 다른 이름이 필요합니다. 예: ${availablePackage(pkg.packageName)}"
                return@setOnClickListener
            }
            dialog.dismiss(); busy = true; status.text = "수정본을 준비하고 있습니다"
            worker.execute {
                try {
                    val output = ApkWorkshop(activity.applicationContext).build(app, spec) { progress -> ui { status.text = progress } }
                    ui { busy = false; status.text = "수정본이 준비됐습니다"; outputActions(output, pkg.packageName) }
                } catch (error: Throwable) {
                    Diagnostics.record(activity, "APK build", error)
                    ui { busy = false; status.text = "APK 생성 실패"; notice(error.message ?: error.javaClass.simpleName) }
                }
            }
        } }
        dialog.show()
    }
    private fun outputActions(output: EditedApk, original: String) {
        val actions = if (output.packageName == original) arrayOf("수정본 설치", "APK 파일 저장", "원본 앱 제거") else arrayOf("수정본 설치", "APK 파일 저장")
        AlertDialog.Builder(activity).setTitle("수정본 준비 완료")
            .setMessage("${output.packageName}\n\n서명 SHA-256\n${output.certificate}\n\n${if (output.packageName == original) "원본과 서명이 다르면 기존 앱을 제거한 뒤 설치해야 합니다. 제거 시 앱 데이터가 삭제됩니다." else "새 패키지이므로 원본과 별도로 설치됩니다."}")
            .setPositiveButton("설치") { _, _ -> install(output.id) }
            .setNeutralButton("저장 / 추가 작업") { _, _ -> AlertDialog.Builder(activity).setTitle("수정본 작업").setItems(actions) { _, index ->
                when (index) { 0 -> install(output.id); 1 -> export(output.id); 2 -> uninstall(original) }
            }.setNegativeButton("닫기", null).show() }
            .setNegativeButton("닫기", null).show()
    }
    private fun showOutputs() {
        val dir = File(activity.filesDir, "edited")
        val items = dir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (items.isEmpty()) { notice("저장된 수정본이 없습니다."); return }
        val records = items.mapNotNull { file -> try { val json = JSONObject(file.readText()); Triple(file.nameWithoutExtension, json, File(dir, "${file.nameWithoutExtension}.apk")) } catch (_: Exception) { null } }.filter { it.third.exists() }
        AlertDialog.Builder(activity).setTitle("저장된 수정본").setItems(records.map { "${it.second.optString("label")}\n${it.second.optString("package")}" }.toTypedArray()) { _, index ->
            val (id, json, file) = records[index]
            outputActions(EditedApk(id, file, json.getString("package"), json.getString("certificate")), json.getString("source"))
        }.setNegativeButton("닫기", null).show()
    }
    private fun uninstall(packageName: String) {
        try {
            val app = pm.getApplicationInfo(packageName, 0)
            if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) { notice("시스템 앱은 일반 권한으로 제거할 수 없습니다."); return }
            AlertDialog.Builder(activity).setTitle("앱을 제거할까요?").setMessage("$packageName\n앱 데이터가 함께 삭제됩니다.")
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
            status.text = "설치 파일 확인 중. 아래 설치 결과를 선택하면 상세 오류를 볼 수 있습니다."
        } catch (error: Exception) { notice("설치 화면을 열지 못했습니다: ${error.message}") }
    }
    fun resume() {
        preferences.getString("pending_install", null)?.let { id -> if (Build.VERSION.SDK_INT < 26 || pm.canRequestPackageInstalls()) install(id) }
        refresh()
    }
    private fun installed(name: String): Boolean = try { pm.getPackageInfo(name, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    private fun availablePackage(original: String): String {
        for (index in 0..99) {
            val name = original + if (index == 0) ".gb" else ".gb$index"
            if (!installed(name)) return name
        }
        return "$original.gb${System.currentTimeMillis().toString().takeLast(6)}"
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
