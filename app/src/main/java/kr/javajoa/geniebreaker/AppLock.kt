package kr.javajoa.geniebreaker

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class AppLock(context: Context) {
    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    private var unlocked = false
    val enabled get() = prefs.contains("pin")
    val locked get() = enabled && !unlocked

    fun relock(): Boolean {
        val changed = enabled && unlocked
        unlocked = false
        return changed
    }

    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..8 || pin.any { it !in '0'..'9' }) return false
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val record = Base64.encodeToString(salt + derive(pin, salt), Base64.NO_WRAP)
        if (!prefs.edit().putString("pin", record).commit()) return false
        unlocked = true
        return true
    }

    fun disable(): Boolean = prefs.edit().remove("pin").commit()

    fun unlock(pin: String): Boolean {
        if (!locked || pin.isEmpty() || pin.length > 12 || pin.any { it !in '0'..'9' }) return false
        val master = BuildConfig.VERSION_NAME.replace(".", "")
        val record = prefs.getString("pin", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        unlocked = pin == master || (record?.size == 48 &&
            MessageDigest.isEqual(record.copyOfRange(16, 48), derive(pin, record.copyOfRange(0, 16))))
        return unlocked
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val key = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).encoded
        } finally {
            key.clearPassword()
        }
    }
}
