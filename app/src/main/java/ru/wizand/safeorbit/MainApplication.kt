package ru.wizand.safeorbit

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

@HiltAndroidApp
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        clearPrefsIfNewInstall()
    }

    private fun clearPrefsIfNewInstall() {
        val appPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//        val clientPrefs = getSharedPreferences("client_prefs", MODE_PRIVATE) // если ты их используешь

        val storedInstallTime = appPrefs.getLong("stored_install_time", -1L)
        val realInstallTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime

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


