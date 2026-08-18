package ru.wizand.safeorbit

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

@HiltAndroidApp
class MainApplication : Application() {

    companion object {
        @Volatile
        private var mapKitInitialized = false

        /**
         * Ленивая однократная инициализация MapKit. Вызывать перед первым
         * использованием карты (до mapView.onStart()), а не в Application.onCreate —
         * экраны выбора роли / PIN / настроек карты не используют и не должны
         * платить за инициализацию MapKit.
         */
        @Synchronized
        fun ensureMapKitInitialized(context: Context) {
            if (mapKitInitialized) return
            try {
                MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
                MapKitFactory.initialize(context.applicationContext)
                mapKitInitialized = true
            } catch (e: Exception) {
                Log.e("MainApplication", "Ошибка инициализации MapKit: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Инициализация Firebase
        FirebaseApp.initializeApp(this)

        // 2. Ленивая инициализация MapKit: больше не выполняется синхронно при старте
        // процесса (это сокращает время onCreate стартовых Activity). MapKit
        // инициализируется один раз перед первым использованием карты —
        // см. ensureMapKitInitialized().

        // 3. Прогрев EncryptedSharedPreferences в фоне, чтобы первое Activity
        // не платило за создание keyset на главном потоке.
        EncryptedPreferencesManager.warmUp(this)

        // 4. Очистка настроек
        clearPrefsIfNewInstall()
    }

    private fun clearPrefsIfNewInstall() {
        val appPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//        val clientPrefs = getSharedPreferences("client_prefs", MODE_PRIVATE) // если ты их используешь

        val storedInstallTime = appPrefs.getLong("stored_install_time", -1L)
        val realInstallTime = try {
            packageManager.getPackageInfo(packageName, 0).firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        if (storedInstallTime == -1L || storedInstallTime != realInstallTime) {
            Log.i("MainApplication", "Новая установка. Очищаем все prefs")

            appPrefs.edit().remove("permissions_intro_shown").apply()

            appPrefs.edit().clear().apply()
//            clientPrefs.edit().clear().apply()

            appPrefs.edit().putLong("stored_install_time", realInstallTime).apply()
        } else {
            Log.d("MainApplication", "Это не новая установка")
        }
    }
}