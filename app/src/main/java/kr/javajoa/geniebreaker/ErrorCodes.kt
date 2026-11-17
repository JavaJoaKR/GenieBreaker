package kr.javajoa.geniebreaker

internal object ErrorCodes {
    fun category(operation: String): String = when {
        Regex("(?i)bluetooth|bond|페어링|블루투스|연결|등록 삭제").containsMatchIn(operation) -> "BT"
        Regex("(?i)install|설치").containsMatchIn(operation) -> "IN"
        Regex("(?i)clone|dual|APK|듀얼").containsMatchIn(operation) -> "CL"
        Regex("(?i)launch|실행").containsMatchIn(operation) -> "AP"
        Regex("(?i)file|storage|파일|폴더|정리").containsMatchIn(operation) -> "FS"
        Regex("(?i)crash|uncaught").containsMatchIn(operation) -> "CR"
        Regex("(?i)update|업데이트").containsMatchIn(operation) -> "UP"
        else -> "UI"
    }
    fun reason(operation: String, type: String?) = type ?: when {
        Regex("(?i)거절|rejected").containsMatchIn(operation) -> "Rejected"
        Regex("(?i)차단|권한|blocked").containsMatchIn(operation) -> "Blocked"
        isError(operation) || operation.contains("failed", true) -> "Failure"
        else -> "Event"
    }
    fun create(operation: String, type: String?): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest("${category(operation)}:${reason(operation, type)}".toByteArray())
            .take(2).joinToString("") { "%02X".format(it) }
        return "GB-${category(operation)}-$hash"
    }
    fun isError(text: String) = Regex("실패|못했|못하|거절|차단|부족|오류|미지원|불가|할 수 없|다시 시도|지원하지|잘못된").containsMatchIn(text)
}
