package ru.wizand.safeorbit.presentation.server.audio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import io.agora.rtc2.*
import ru.wizand.safeorbit.R

class AudioBroadcastService : Service() {

    private lateinit var rtcEngine: RtcEngine
    private val channelName = "safeorbit_audio"
    private var serverId: String? = null

    override fun onCreate() {
        super.onCreate()
        // Нельзя запускать тут, пока нет интента → перенесено в onStartCommand
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AUDIO_STREAM", "🎙️ AudioBroadcastService запускается")

        serverId = intent?.getStringExtra("server_id")
        if (serverId.isNullOrEmpty()) {
            Log.e("AUDIO_STREAM", "❌ serverId не передан")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d("AUDIO_STREAM", "📦 Сервис запущен. serverId=$serverId")

        // Проверка разрешений
        if (!hasAudioPermissions()) {
            Log.e("AUDIO_STREAM", "❌ Не хватает разрешений для записи звука")
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()

        val appId = try {
            applicationContext.packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("AGORA_APP_ID")
        } catch (e: Exception) {
            Log.e("AUDIO_STREAM", "❌ Не удалось получить AGORA_APP_ID: ${e.message}")
            null
        }

        if (appId.isNullOrBlank()) {
            Log.e("AUDIO_STREAM", "❌ AGORA_APP_ID пуст — остановка сервиса")
            stopSelf()
            return START_NOT_STICKY
        }

        initAgora(appId)
        joinChannel()

        // ⏱️ Остановка через 60 сек (или 10 — для отладки)
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("AUDIO_STREAM", "🕒 Время трансляции истекло — остановка")
            stopSelf()
        }, 60_000)

        return START_NOT_STICKY
    }

    private fun hasAudioPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) == PackageManager.PERMISSION_GRANTED
                else true
    }

    private fun initAgora(appId: String) {
        try {
            rtcEngine = RtcEngine.create(applicationContext, appId, object : IRtcEngineEventHandler() {})
        } catch (e: Exception) {
            Log.e("AUDIO_STREAM", "❌ Ошибка инициализации Agora: ${e.message}")
            stopSelf()
            return
        }

        rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        rtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
        rtcEngine.enableAudio()
    }

    private fun joinChannel() {
        rtcEngine.joinChannel(null, channelName, "", 0)
        Log.d("AUDIO_STREAM", "📡 Подключение к каналу $channelName")
    }

    private fun leaveChannel() {
        try {
            rtcEngine.leaveChannel()
            RtcEngine.destroy()
            Log.d("AUDIO_STREAM", "🔌 Отключено от канала Agora")
        } catch (e: Exception) {
            Log.e("AUDIO_STREAM", "❌ Ошибка при отключении: ${e.message}")
        }
    }

    private fun startForegroundService() {
        val channelId = "audio_stream_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Stream",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeOrbit")
            .setContentText("Идёт аудиотрансляция")
            .setSmallIcon(R.drawable.ic_mic)
            .build()

        startForeground(2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        leaveChannel()
        Log.d("AUDIO_STREAM", "🛑 Сервис остановлен")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
