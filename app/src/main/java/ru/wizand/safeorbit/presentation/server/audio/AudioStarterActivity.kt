package ru.wizand.safeorbit.presentation.server.audio

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class AudioStarterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }

        val serviceIntent = Intent(this, AudioBroadcastService::class.java).apply {
            putExtra("code", intent.getStringExtra("code"))
        }
        startService(serviceIntent)

        // закрытие через 0.5 секунды
        Handler(Looper.getMainLooper()).postDelayed({
            finishAndRemoveTask()
        }, 500)
    }
}