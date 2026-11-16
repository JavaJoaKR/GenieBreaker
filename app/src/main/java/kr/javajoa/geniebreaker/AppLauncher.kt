package kr.javajoa.geniebreaker

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.widget.EditText

/** Launches exported app entry points directly; local labels do not change installed APKs. */
internal class AppLauncher(private val activity: Activity) {
    private val aliases = activity.getSharedPreferences("app_aliases", Context.MODE_PRIVATE)
    @Suppress("DEPRECATION")
    fun show() {
        val pm = activity.packageManager
        val entries = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { category ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.filter { it.activityInfo.packageName != activity.packageName }
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(pm).toString().lowercase() }
        if (entries.isEmpty()) { AlertDialog.Builder(activity).setMessage("실행 가능한 앱을 찾지 못했습니다.").setPositiveButton("닫기", null).show(); return }
        val labels = entries.map { entry ->
            val pkg = entry.activityInfo.packageName
            "${aliases.getString(pkg, null) ?: entry.loadLabel(pm)}\n$pkg"
        }.toTypedArray()
        AlertDialog.Builder(activity).setTitle("앱 선택").setItems(labels) { _, index ->
            val entry = entries[index]; val pkg = entry.activityInfo.packageName
            AlertDialog.Builder(activity).setTitle(aliases.getString(pkg, null) ?: entry.loadLabel(pm))
                .setMessage("$pkg\n\n표시 이름은 GenieBreaker 안에서만 변경됩니다. 패키지명·서명·DRM은 변경하지 않습니다.")
                .setPositiveButton("실행") { _, _ ->
                    try {
                        activity.startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, entry.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (error: Exception) {
                        Diagnostics.record(activity, "Launch $pkg", error)
                        AlertDialog.Builder(activity).setTitle("실행할 수 없습니다").setMessage("${error.javaClass.simpleName}: ${error.message}\n\n오류 기록에 저장했습니다.").setPositiveButton("닫기", null).show()
                    }
                }
                .setNeutralButton("표시 이름 변경") { _, _ ->
                    val input = EditText(activity).apply { setSingleLine(); setText(aliases.getString(pkg, null) ?: entry.loadLabel(pm)); selectAll() }
                    AlertDialog.Builder(activity).setTitle("앱 표시 이름").setView(input).setMessage("이 앱의 목록에만 적용됩니다. 비우면 원래 이름으로 돌아갑니다.")
                        .setPositiveButton("저장") { _, _ ->
                            val value = input.text.toString().trim().take(80)
                            if (value.isEmpty()) aliases.edit().remove(pkg).apply() else aliases.edit().putString(pkg, value).apply()
                            show()
                        }.setNegativeButton("취소", null).show()
                }.setNegativeButton("닫기", null).show()
        }.setNegativeButton("닫기", null).show()
    }
}
