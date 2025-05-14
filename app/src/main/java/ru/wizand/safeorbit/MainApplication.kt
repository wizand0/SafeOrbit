package ru.wizand.safeorbit

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Инициализация Firebase (необязательна, если в манифесте auto-init = true)
        FirebaseApp.initializeApp(this)

        // Пример инициализации карты или логирования
        // YandexMapKitFactory.setApiKey("API_KEY")

        // Установка дефолтных значений или поведения
        Log.d("App", "Приложение запущено")
    }
}
