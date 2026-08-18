package ru.wizand.safeorbit.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

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
        private const val KEY_SERVER_CODE = "server_code"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_PIN_VERIFIED = "pin_verified"

        // Migration flag
        private const val KEY_MIGRATION_DONE = "migration_done_v1"

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
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка создания EncryptedSharedPreferences: ${e.message}", e)
            // Fallback на обычные prefs при ошибке
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Миграция данных из старых незащищённых SharedPreferences
     */
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

    // ========= PIN управление =========

    /**
     * Сохранить PIN-код сервера
     */
    fun savePin(pin: String) {
        securePrefs.edit().putString(KEY_SERVER_PIN, pin).apply()
        Log.d(TAG, "PIN сохранён")
    }

    /**
     * Получить PIN-код сервера
     */
    fun getPin(): String? {
        return securePrefs.getString(KEY_SERVER_PIN, null)
    }

    /**
     * Проверить, установлен ли PIN
     */
    fun hasPin(): Boolean {
        return getPin() != null
    }

    /**
     * Удалить PIN
     */
    fun clearPin() {
        securePrefs.edit().remove(KEY_SERVER_PIN).apply()
        Log.d(TAG, "PIN удалён")
    }

    /**
     * Установить статус верификации PIN
     */
    fun setPinVerified(verified: Boolean) {
        securePrefs.edit().putBoolean(KEY_PIN_VERIFIED, verified).apply()
    }

    /**
     * Проверить, верифицирован ли PIN
     */
    fun isPinVerified(): Boolean {
        return securePrefs.getBoolean(KEY_PIN_VERIFIED, false)
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