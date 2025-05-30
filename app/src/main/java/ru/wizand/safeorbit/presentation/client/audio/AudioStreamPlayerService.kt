// Клиентский сервис прослушивания
package ru.wizand.safeorbit.presentation.client.audio

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import io.agora.rtc2.*
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME
import java.util.*

class AudioStreamPlayerService : Service() {

    private var rtcEngine: RtcEngine? = null
    private val channelName = "safeorbit_audio"

    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        Log.w("AUDIO_CLIENT", "⏱️ Время прослушивания истекло, остановка сервиса")
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val role = prefs.getString("user_role", null)
        if (role != UserRole.CLIENT.name) {
            Log.w("AUDIO_CLIENT", "❌ Неверная роль: $role. Сервис не запущен.")
            stopSelf()
            return
        }

        Log.d("AUDIO_CLIENT", "🟢 Сервис клиента создан")
        startForegroundService()
        initAgora()
        joinChannel()

        // Завершить прослушивание через 10 минут
        stopHandler.postDelayed(stopRunnable, 10 * 60 * 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AUDIO_CLIENT", "🔴 Сервис клиента уничтожается")
        leaveChannel()

        stopHandler.removeCallbacks(stopRunnable)
    }

    private fun initAgora() {
        try {
            val appId = applicationContext.packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("AGORA_APP_ID")

            if (appId.isNullOrEmpty()) {
                Log.e("AUDIO_CLIENT", "❌ AGORA_APP_ID отсутствует")
                stopSelf()
                return
            }

            rtcEngine = RtcEngine.create(applicationContext, appId, object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    Log.d("AUDIO_CLIENT", "✅ Подключились к каналу $channel с uid=$uid")
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    Log.d("AUDIO_CLIENT", "🎧 Присоединился вещатель: $uid")
                }

                override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
                    Log.d("AUDIO_CLIENT", "🎧 Состояние аудио от $uid: state=$state, reason=$reason")
                }
                override fun onError(err: Int) {
                    Log.e("AUDIO_CLIENT", "❌ Ошибка Agora: $err")
                }
            })

//            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)

            rtcEngine?.setClientRole(Constants.CLIENT_ROLE_AUDIENCE)
            rtcEngine?.enableAudio()
            rtcEngine?.enableAudioVolumeIndication(200, 3, true)
            rtcEngine?.setEnableSpeakerphone(true)
            rtcEngine?.muteAllRemoteAudioStreams(false)

            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true

        } catch (e: Exception) {
            Log.e("AUDIO_CLIENT", "❌ Ошибка инициализации Agora: ${e.message}")
            stopSelf()
        }
    }

    private fun joinChannel() {
        val clientUid = UUID.randomUUID().hashCode() and 0x0FFFFFFF
        rtcEngine?.joinChannel(null, channelName, "", clientUid)
        Log.d("AUDIO_CLIENT", "📡 Присоединение к каналу $channelName с uid=$clientUid")
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