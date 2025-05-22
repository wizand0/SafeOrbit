package ru.wizand.safeorbit.presentation.client.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.agora.rtc2.*
import ru.wizand.safeorbit.R

class AudioStreamPlayerService : Service() {

    private var rtcEngine: RtcEngine? = null
    private val channelName = "safeorbit_audio"

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initAgora()
        joinChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        leaveChannel()
    }

    private fun initAgora() {
        try {
            val appId = applicationContext.packageManager
                .getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
                .metaData?.getString("AGORA_APP_ID")

            if (appId.isNullOrEmpty()) {
                Log.e("AUDIO_CLIENT", "❌ AGORA_APP_ID отсутствует")
                stopSelf()
                return
            }

            rtcEngine = RtcEngine.create(applicationContext, appId, object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    Log.d("AUDIO_CLIENT", "🎧 Присоединился вещатель: $uid")
                }

                override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
                    Log.d("AUDIO_CLIENT", "🎧 Состояние аудио от $uid: state=$state, reason=$reason")
                }
            })

            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine?.setClientRole(Constants.CLIENT_ROLE_AUDIENCE)
            rtcEngine?.enableAudio()
        } catch (e: Exception) {
            Log.e("AUDIO_CLIENT", "❌ Ошибка инициализации Agora: ${e.message}")
            stopSelf()
        }
    }

    private fun joinChannel() {
        rtcEngine?.joinChannel(null, channelName, "", 0)
        Log.d("AUDIO_CLIENT", "📡 Присоединение к каналу $channelName")
    }

    private fun leaveChannel() {
        try {
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
            Log.d("AUDIO_CLIENT", "🔌 Покинул канал $channelName")
        } catch (e: Exception) {
            Log.e("AUDIO_CLIENT", "❌ Ошибка при отключении: ${e.message}")
        }
    }

    private fun startForegroundService() {
        val channelId = "audio_playback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeOrbit")
            .setContentText("Прослушивание аудиотрансляции")
            .setSmallIcon(R.drawable.ic_mic)
            .build()

        startForeground(3, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
