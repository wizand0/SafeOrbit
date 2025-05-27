package ru.wizand.safeorbit.presentation.server.audio

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SilentAudioLaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AUDIO_LAUNCH", "📦 onCreate SilentAudioLaunchActivity")

        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        Log.d("AUDIO_LAUNCH", "onCreate SilentAudioLaunchActivity window.addFlags")

        // Запуск сервиса
        val intent = Intent(this, AudioBroadcastService::class.java)
        Log.d("AUDIO_LAUNCH", "📦 Запуск аудиосервиса Intent(this, AudioBroadcastService::class.java)")
        ContextCompat.startForegroundService(this, intent)
        Log.d("AUDIO_LAUNCH", "📦 Запуск аудиосервиса ContextCompat.startForegroundService(this, intent)")

        // Завершение через 1 секунду
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("AUDIO_LAUNCH", "Handler(Looper.getMainLooper()).postDelayed")
            finish()
        }, 1000)
        Log.d("AUDIO_LAUNCH", "finish() 1000")
    }
}

