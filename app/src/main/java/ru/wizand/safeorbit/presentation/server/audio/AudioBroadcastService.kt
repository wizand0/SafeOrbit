// Серверный сервис трансляции
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

    private var rtcEngine: RtcEngine? = null
    private val channelName = "safeorbit_audio"
    private var serverId: String? = null

    // В начало класса добавь:
    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        Log.w("AUDIO_SERVER", "⏱️ Максимальное время трансляции истекло, остановка сервиса")

        // Освобождаем WakeLock при автоостановке, если не успели попасть в onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
        }

        stopSelf()
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()

        serverId = intent?.getStringExtra("server_id") ?: ""
        if (serverId!!.isEmpty()) {
            Log.e("AUDIO_SERVER", "❌ serverId не передан")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!hasAudioPermissions()) {
            Log.e("AUDIO_SERVER", "❌ Нет разрешений для записи")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!safeStartForeground()) {
            // foreground не стартанул — ждём действия пользователя
            return START_NOT_STICKY
        }

        val appId = try {
            applicationContext.packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("AGORA_APP_ID")
        } catch (e: Exception) {
            Log.e("AUDIO_SERVER", "❌ Не удалось получить AGORA_APP_ID: ${e.message}")
            null
        }

        if (appId.isNullOrBlank()) {
            Log.e("AUDIO_SERVER", "❌ AGORA_APP_ID пуст")
            stopSelf()
            return START_NOT_STICKY
        }

        initAgora(appId)
        joinChannel()

        // Остановить через 10 минут (600_000 мс)
        stopHandler.postDelayed(stopRunnable, 10 * 60 * 1000L)

        return START_NOT_STICKY


    }

    private fun startForegroundSafely() {
        val channelId = "audio_fallback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Stream",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("SafeOrbit")
            .setContentText("Трансляция аудио активна")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1002, notification)
    }


    private fun hasAudioPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED &&
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                    ) == PackageManager.PERMISSION_GRANTED
                else true
    }

    private fun initAgora(appId: String) {
        try {
            rtcEngine =
                RtcEngine.create(applicationContext, appId, object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                        Log.d("AUDIO_SERVER", "✅ Подключились к каналу $channel с uid=$uid")
                    }

                    override fun onRemoteAudioStateChanged(
                        uid: Int,
                        state: Int,
                        reason: Int,
                        elapsed: Int
                    ) {
                        Log.d(
                            "AUDIO_CLIENT",
                            "🎧 remote audio state: uid=$uid, state=$state, reason=$reason, elapsed=$elapsed"
                        )
                    }

                    override fun onError(err: Int) {
                        Log.e("AUDIO_CLIENT", "❌ Ошибка Agora: $err")
                    }

                })
        } catch (e: Exception) {
            Log.e("AUDIO_SERVER", "❌ Ошибка инициализации Agora: ${e.message}")
            stopSelf()
            return
        }

//        rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
        rtcEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
        rtcEngine?.enableAudio()
        rtcEngine?.enableLocalAudio(true)
        rtcEngine?.muteLocalAudioStream(false) // 🔈 Это обязательно!
        rtcEngine?.enableAudioVolumeIndication(200, 3, true)

        rtcEngine?.adjustRecordingSignalVolume(200)
        rtcEngine?.setEnableSpeakerphone(false)


        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
    }

    private fun joinChannel() {
        val serverUid = serverId.hashCode() and 0x0FFFFFFF
        rtcEngine?.joinChannel(null, channelName, "", serverUid)
        Log.d("AUDIO_SERVER", "📡 Присоединились к $channelName с uid=$serverUid")
    }


    private fun leaveChannel() {
        try {
            rtcEngine?.leaveChannel()
            Log.d("AUDIO_SERVER", "🔌 Покинули канал")
        } catch (e: Exception) {
            Log.e("AUDIO_SERVER", "❌ Ошибка при выходе: ${e.message}")
        }

        try {
            RtcEngine.destroy()

        } catch (e: Exception) {
            Log.e("AUDIO_SERVER", "❌ Ошибка при destroy(): ${e.message}")
        }
        rtcEngine = null
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

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeOrbit:AudioLock")
        wakeLock?.acquire(10 * 60 * 1000L) // 10 минут

        startForeground(2, notification)
    }

    private fun safeStartForeground(): Boolean {
        return try {
            startForeground(2, buildNotification())
            true
        } catch (e: SecurityException) {
            Log.e("AUDIO_SERVER", "🚫 Не удалось запустить FGS с микрофоном: ${e.message}")
            showFallbackNotification()
            false
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "audio_stream_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Stream",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeOrbit")
            .setContentText("Идёт аудиотрансляция")
            .setSmallIcon(R.drawable.ic_mic)
            .build()
    }

    private fun showFallbackNotification() {
        val channelId = "audio_fallback_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Разрешите трансляцию звука",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val fallbackIntent = Intent(this, AudioLaunchActivity::class.java).apply {
            putExtra("server_id", serverId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1002,
            fallbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("SafeOrbit")
            .setContentText("⚠️ Требуется ваше действие для запуска трансляции")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_mic, "Начать трансляцию", pendingIntent)
            .build()

        manager.notify(1002, notification)
    }


    override fun onDestroy() {
        super.onDestroy()

        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
        }

        Log.d("AUDIO_SERVER", "🛑 Сервис трансляции завершён")
        leaveChannel()

        stopHandler.removeCallbacks(stopRunnable)

    }

    override fun onBind(intent: Intent?): IBinder? = null
}
