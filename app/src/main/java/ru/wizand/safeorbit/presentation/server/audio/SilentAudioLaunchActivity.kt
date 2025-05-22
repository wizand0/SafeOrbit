package ru.wizand.safeorbit.presentation.server.audio

import android.content.Intent
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SilentAudioLaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverId = intent.getStringExtra("server_id")
        if (serverId.isNullOrEmpty()) {
            Log.e("AUDIO_LAUNCH", "❌ serverId не передан")
            finish()
            return
        }

        Log.d("AUDIO_LAUNCH", "📦 Запуск аудиосервиса с serverId=$serverId")

        val serviceIntent = Intent(this, AudioBroadcastService::class.java).apply {
            putExtra("server_id", serverId)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        finish()
    }


//    override fun onResume() {
//        super.onResume()
//
//        val serverId = intent.getStringExtra("server_id")
//
//        Handler(Looper.getMainLooper()).postDelayed({
//            val serviceIntent = Intent(this, AudioBroadcastService::class.java).apply {
//                putExtra("server_id", serverId)
//            }
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                Log.d("SILENT_AUDIO", "🚀 Запуск AudioBroadcastService")
//                startForegroundService(serviceIntent)
//            } else {
//                startService(serviceIntent)
//            }
//
//            finish()
//        }, 500) // Задержка 500 мс — минимально надёжно
//    }
}
