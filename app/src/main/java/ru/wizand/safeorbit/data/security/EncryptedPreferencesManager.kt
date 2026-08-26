package ru.wizand.safeorbit.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Менеджер для безопасного хранения чувствительных данных.
 *
 * Использует EncryptedSharedPreferences для API >= 23,
 * fallback на обычные SharedPreferences для более старых версий.
 *
 * Автоматически мигрирует данные из незащищённых prefs при первом запуске.
 */
class EncryptedPreferencesManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "EncryptedPrefsManager"
        private const val ENCRYPTED_PREFS_NAME = "safeorbit_secure_prefs"

        // Keys
        private const val KEY_SERVER_PIN = "server_pin"
        private const val KEY_PIN_SALT = "server_pin_salt"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_SERVER_CODE = "server_code"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_PIN_VERIFIED = "pin_verified"

        // Migration flag
        private const val KEY_MIGRATION_DONE = "migration_done_v1"

        // 1.7 (аудит): PIN хранится только как PBKDF2-хэш
        private const val PIN_HASH_PREFIX = "pbkdf2sha256:"
        private const val PIN_PBKDF2_ITERATIONS = 60_000
        private const val PIN_HASH_BITS = 256
        private const val PIN_SALT_BYTES = 16

        @Volatile
        private var instance: EncryptedPreferencesManager? = null

        /**
         * Единственный экземпляр на процесс.
         *
         * Создание EncryptedSharedPreferences (Tink keyset) — дорогая синхронная операция
         * (сотни мс), поэтому экземпляр создаётся один раз и переиспользуется всеми
         * Activity/Service вместо повторного создания в каждом onCreate.
         */
        fun getInstance(context: Context): EncryptedPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: EncryptedPreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * Предварительная инициализация в фоновом потоке (вызывать из Application.onCreate),
         * чтобы к моменту первого Activity обращение к prefs было почти мгновенным.
         */
        fun warmUp(context: Context) {
            Thread({
                try {
                    getInstance(context).getUserRole()
                    Log.d(TAG, "Фоновый прогрев EncryptedPreferences завершён")
                } catch (e: Exception) {
                    Log.w(TAG, "Фоновый прогрев EncryptedPreferences не удался: ${e.message}")
                }
            }, "encrypted-prefs-warmup").start()
        }
    }

    private val securePrefs: SharedPreferences by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            createEncryptedPreferences()
        } else {
            // Fallback для API < 23
            Log.w(TAG, "EncryptedSharedPreferences недоступны, используется обычный режим")
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val legacyPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        // Автоматическая миграция при инициализации
        migrateFromLegacyPrefsIfNeeded()
    }

    /**
     * Создаёт EncryptedSharedPreferences с использованием MasterKey
     */
    private fun createEncryptedPreferences(): SharedPreferences {
        return try {
            createEncryptedPreferencesOrThrow()
        } catch (first: Exception) {
            Log.e(TAG, "Ошибка EncryptedSharedPreferences, пересоздаю keyset: ${first.message}", first)
            // Однократная попытка пересоздания (битый keyset Tink).
            // 1.7 (аудит): тихий fallback на НЕзашифрованные prefs удалён —
            // явное падение лучше хранения PIN/кода в открытом виде.
            try {
                context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                createEncryptedPreferencesOrThrow()
            } catch (second: Exception) {
                throw IllegalStateException("EncryptedSharedPreferences недоступен", second)
            }
        }
    }

    private fun createEncryptedPreferencesOrThrow(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

        private fun migrateFromLegacyPrefsIfNeeded() {
        if (securePrefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            Log.d(TAG, "Миграция уже выполнена ранее")
            return
        }

        try {
            val editor = securePrefs.edit()
            var migrated = false

            // Миграция PIN
            legacyPrefs.getString(KEY_SERVER_PIN, null)?.let { pin ->
                editor.putString(KEY_SERVER_PIN, pin)
                migrated = true
                Log.d(TAG, "Мигрирован server_pin")
            }

            // Миграция server_code
            legacyPrefs.getString(KEY_SERVER_CODE, null)?.let { code ->
                editor.putString(KEY_SERVER_CODE, code)
                migrated = true
                Log.d(TAG, "Мигрирован server_code")
            }

            // Миграция server_id
            legacyPrefs.getString(KEY_SERVER_ID, null)?.let { id ->
                editor.putString(KEY_SERVER_ID, id)
                migrated = true
                Log.d(TAG, "Мигрирован server_id")
            }

            // Миграция user_role
            legacyPrefs.getString(KEY_USER_ROLE, null)?.let { role ->
                editor.putString(KEY_USER_ROLE, role)
                migrated = true
                Log.d(TAG, "Мигрирован user_role")
            }

            // Миграция pin_verified
            if (legacyPrefs.contains(KEY_PIN_VERIFIED)) {
                val verified = legacyPrefs.getBoolean(KEY_PIN_VERIFIED, false)
                editor.putBoolean(KEY_PIN_VERIFIED, verified)
                migrated = true
                Log.d(TAG, "Мигрирован pin_verified")
            }

            // Помечаем миграцию как выполненную
            editor.putBoolean(KEY_MIGRATION_DONE, true)
            editor.apply()

            if (migrated) {
                // Удаляем чувствительные данные из старых prefs
                legacyPrefs.edit()
                    .remove(KEY_SERVER_PIN)
                    .remove(KEY_SERVER_CODE)
                    .remove(KEY_SERVER_ID)
                    .apply()

                Log.i(TAG, "Миграция завершена успешно, старые данные удалены")
            } else {
                Log.d(TAG, "Нечего мигрировать")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка миграции: ${e.message}", e)
        }
    }

    // ========= PIN-хранилище =========

    /**
     * 1.7 (аудит): сохраняет НЕ plaintext, а PBKDF2-HMAC-SHA256 хэш с локальной солью.
     */
    fun savePin(pin: String) {
        securePrefs.edit()
            .putString(KEY_SERVER_PIN, hashPin(pin))
            .remove(KEY_PIN_FAILED_ATTEMPTS)
            .remove(KEY_PIN_LOCKOUT_UNTIL)
            .apply()
        Log.d(TAG, "PIN сохранён (хэш)")
    }

    /**
     * Проверка PIN. Поддерживает легаси-значения (plaintext внутри зашифрованных
     * prefs): при совпадении прозрачно мигрирует значение на хэш.
     */
    fun verifyPin(pin: String): Boolean {
        val stored = securePrefs.getString(KEY_SERVER_PIN, null) ?: return false
        return if (stored.startsWith(PIN_HASH_PREFIX)) {
            constantTimeEquals(stored, hashPin(pin))
        } else if (constantTimeEquals(stored, pin)) {
            securePrefs.edit().putString(KEY_SERVER_PIN, hashPin(pin)).apply()
            Log.i(TAG, "PIN мигрирован с plaintext на хэш")
            true
        } else {
            false
        }
    }

    /** Установлен ли PIN. */
    fun hasPin(): Boolean = securePrefs.getString(KEY_SERVER_PIN, null) != null

    /**
     * Совместимость с существующим UI: наружу возвращается только состояние
     * успешной проверки, но не PIN и не его хэш.
     */
    fun setPinVerified(verified: Boolean) {
        securePrefs.edit().putBoolean(KEY_PIN_VERIFIED, verified).apply()
    }

    fun isPinVerified(): Boolean =
        securePrefs.getBoolean(KEY_PIN_VERIFIED, false)

    /** Удаление PIN и счётчиков. */
    fun clearPin() {
        securePrefs.edit()
            .remove(KEY_SERVER_PIN)
            .remove(KEY_PIN_FAILED_ATTEMPTS)
            .remove(KEY_PIN_LOCKOUT_UNTIL)
            .apply()
        Log.d(TAG, "PIN удалён")
    }

    // --- 1.7: персистентный счётчик неудачных попыток (переживает перезапуск процесса) ---

    fun getPinFailedAttempts(): Int = securePrefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)

    fun setPinFailedAttempts(attempts: Int) {
        securePrefs.edit().putInt(KEY_PIN_FAILED_ATTEMPTS, attempts).apply()
    }

    fun getPinLockoutUntil(): Long = securePrefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)

    fun setPinLockoutUntil(untilMs: Long) {
        securePrefs.edit().putLong(KEY_PIN_LOCKOUT_UNTIL, untilMs).apply()
    }

    private fun getOrCreatePinSalt(): ByteArray {
        val existing = securePrefs.getString(KEY_PIN_SALT, null)
        if (existing != null) return Base64.getDecoder().decode(existing)
        val salt = ByteArray(PIN_SALT_BYTES).also { SecureRandom().nextBytes(it) }
        securePrefs.edit().putString(KEY_PIN_SALT, Base64.getEncoder().encodeToString(salt)).apply()
        return salt
    }

    private fun hashPin(pin: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), getOrCreatePinSalt(), PIN_PBKDF2_ITERATIONS, PIN_HASH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            return PIN_HASH_PREFIX + Base64.getEncoder().encodeToString(hash)
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    // ========= Pairing Token управление =========

    /**
     * Сохранить pairing token
     */
    fun savePairingToken(token: String) {
        securePrefs.edit().putString("pairing_token", token).apply()
        Log.d(TAG, "Pairing token сохранён")
    }

    /**
     * Получить pairing token
     */
    fun getPairingToken(): String? {
        return securePrefs.getString("pairing_token", null)
    }

    // ========= Server ID управление =========

    /**
     * Сохранить ID сервера
     */
    fun saveServerId(serverId: String) {
        securePrefs.edit().putString(KEY_SERVER_ID, serverId).apply()
        Log.d(TAG, "Server ID сохранён")
    }

    /**
     * Получить ID сервера
     */
    fun getServerId(): String? {
        return securePrefs.getString(KEY_SERVER_ID, null)
    }

    // ========= Server Code управление =========

    /**
     * Сохранить код сервера
     */
    fun saveCode(code: String) {
        securePrefs.edit().putString(KEY_SERVER_CODE, code).apply()
        Log.d(TAG, "Server code сохранён")
    }

    /**
     * Получить код сервера
     */
    fun getCode(): String? {
        return securePrefs.getString(KEY_SERVER_CODE, null)
    }

    // ========= User Role управление =========

    /**
     * Сохранить роль пользователя
     */
    fun saveUserRole(role: String) {
        securePrefs.edit().putString(KEY_USER_ROLE, role).apply()
        Log.d(TAG, "User role сохранена: $role")
    }

    /**
     * Получить роль пользователя
     */
    fun getUserRole(): String? {
        return securePrefs.getString(KEY_USER_ROLE, null)
    }

    // ========= Общие методы =========

    /**
     * Полная очистка всех защищённых данных
     */
    fun clearAll() {
        securePrefs.edit().clear().apply()
        Log.i(TAG, "Все защищённые данные удалены")
    }

    /**
     * Проверка, зарегистрирован ли сервер
     */
    fun isServerRegistered(): Boolean {
        return getServerId() != null && getCode() != null
    }
}