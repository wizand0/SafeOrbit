package ru.wizand.safeorbit.utils

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64

/**
 * Шифрование содержимого уведомлений (AES-256-GCM).
 *
 * Ключ деривируется из кода пары (serverId + code) через PBKDF2-HMAC-SHA256.
 * Уведомления в Firebase хранятся зашифрованными: прочитать их может только
 * клиент, знающий код подключения. GCM даёт и шифрование, и аутентификацию —
 * запись с неверным кодом просто не расшифруется.
 */
object CryptoUtils {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_LENGTH = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 60_000

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun deriveKey(serverId: String, code: String): SecretKey {
        val salt = ("SafeOrbit:" + serverId).toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(code.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(key: SecretKey, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(GCM_IV_LENGTH + ciphertext.size)
        iv.copyInto(out, 0)
        ciphertext.copyInto(out, GCM_IV_LENGTH)
        return Base64.getEncoder().encodeToString(out)
    }

    /**
     * @return расшифрованный текст или null, если ключ неверный / данные повреждены.
     */
    fun decryptOrNull(key: SecretKey, encoded: String): String? {
        return try {
            val raw = Base64.getDecoder().decode(encoded)
            if (raw.size <= GCM_IV_LENGTH) return null
            val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
