package kr.javajoa.geniebreaker

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager

internal fun showDiagnostics(owner: Activity) {
    val summary = Diagnostics.recentCauses(owner).take(2).joinToString("\n\n")
        .ifEmpty { "최근 진단 기록이 없습니다." }
    val actions = listOf("상세 기록 보기", "기록 복사", "기록 지우기", "닫기")
    TvMenus.show(owner, "문제 진단", actions, summary, recordFailure = false) { choice ->
        when (choice) {
            0 -> TvMenus.show(owner, "상세 기록", listOf("기록 복사", "닫기"),
                Diagnostics.read(owner), recordFailure = false) {
                if (it == 0) copyDiagnostics(owner)
            }
            1 -> copyDiagnostics(owner)
            2 -> TvMenus.show(owner, "문제 기록 지우기", listOf("취소", "기록 지우기"),
                "기기에 저장된 오류 기록을 삭제합니다.") {
                if (it == 1) Diagnostics.clear(owner)
            }
        }
    }
}

private fun copyDiagnostics(owner: Activity) {
    val clipboard = owner.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("GenieBreaker local errors", Diagnostics.read(owner)))
}
