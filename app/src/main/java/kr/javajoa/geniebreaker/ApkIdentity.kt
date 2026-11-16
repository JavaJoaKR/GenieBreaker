package kr.javajoa.geniebreaker

import java.security.MessageDigest

/** Identity names must not collide with the source, including globally named declarations. */
internal object ApkIdentity {
    private fun suffix(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).take(10).joinToString("") { "%02x".format(it) }
    fun permission(name: String, original: String, target: String): String = when {
        target == original -> name
        name.startsWith("$original.") -> target + name.removePrefix(original)
        else -> "$target.permission.p${suffix(name)}"
    }
    fun authority(name: String, original: String, target: String): String = when {
        target == original -> name
        name == original || name.startsWith("$original.") -> target + name.removePrefix(original)
        else -> "$target.provider.p${suffix(name)}"
    }
}
