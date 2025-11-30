package ru.wizand.safeorbit

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

@HiltAndroidApp
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Инициализация Firebase
        FirebaseApp.initializeApp(this)

        // 2. Инициализация MapKit (Этап 1: Перенос для стабильности)
        // Инициализируем карты один раз при старте процесса приложения.
        // Это предотвращает краш при восстановлении процесса на экране карты.
        try {
            MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
            MapKitFactory.initialize(this)
        } catch (e: Exception) {
            Log.e("MainApplication", "Ошибка инициализации MapKit: ${e.message}")
        }

        // 3. Очистка настроек
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