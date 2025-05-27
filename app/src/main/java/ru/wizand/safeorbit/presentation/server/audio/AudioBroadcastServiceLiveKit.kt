//package ru.wizand.safeorbit.presentation.server.audio
//
//import android.Manifest
//import android.app.*
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.*
//import android.util.Log
//import androidx.core.app.ActivityCompat
//import androidx.core.app.NotificationCompat
//import io.livekit.android.LiveKit
//import io.livekit.android.room.Room
//import io.livekit.android.events.RoomEvent
//import io.livekit.android.events.collect
//import io.livekit.android.room.track.LocalAudioTrack
//import kotlinx.coroutines.*
//import ru.wizand.safeorbit.R
//
//class AudioBroadcastServiceLiveKit : Service() {
//
//    private var serverId: String? = null
//    private lateinit var room: Room
//    private var audioTrack: LocalAudioTrack? = null
//    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        serverId = intent?.getStringExtra("server_id")
//        if (serverId.isNullOrEmpty()) {
//            Log.e("LIVEKIT", "❌ serverId не передан")
//            stopSelf()
//            return START_NOT_STICKY
//        }
//
//        if (!hasAudioPermissions()) {
//            Log.e("LIVEKIT", "❌ Нет разрешения на RECORD_AUDIO")
//            stopSelf()
//            return START_NOT_STICKY
//        }
//
//        startForegroundService()
//        startLiveKitAudio()
//
//        Handler(Looper.getMainLooper()).postDelayed({
//            Log.d("LIVEKIT", "🕒 Время истекло, остановка сервиса")
//            stopSelf()
//        }, 60_000)
//
//        return START_NOT_STICKY
//    }
//
//    private fun hasAudioPermissions(): Boolean {
//        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
//                    ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) == PackageManager.PERMISSION_GRANTED
//                else true
//    }
//
//    private fun startLiveKitAudio() {
//        val url = " "
//        val token = " "
//
//        coroutineScope.launch {
//            try {
//                room = LiveKit.create(applicationContext)
//                room.connect(url, token)
//                // Подписка на события комнаты
//                launch {
//                    room.events.collect { event ->
//                        when (event) {
//                            is RoomEvent.Disconnected -> {
//                                Log.d("LIVEKIT", "❌ Отключено: ${event.reason}")
//                            }
//                            is RoomEvent.TrackPublished -> {
//                                Log.d("LIVEKIT", "✅ Аудио-трек опубликован")
//                            }
//                            is RoomEvent.Reconnecting -> {
//                                Log.d("LIVEKIT", "📶 Состояние: Reconnecting")
//                            }
//                            is RoomEvent.Reconnected  -> {
//                                Log.d("LIVEKIT", "📶 Состояние: Reconnected")
//                            }
//                            else -> {
//                                Log.d("LIVEKIT", "Событие: ${event::class.simpleName}")
//                            }
//                        }
//                    }
//                }
//
//                val localParticipant = room.localParticipant
//                audioTrack = localParticipant.createAudioTrack("microphone")
//
//            } catch (e: Exception) {
//                Log.e("LIVEKIT", "❌ Ошибка подключения: ${e.message}")
//                stopSelf()
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        coroutineScope.cancel()
//        audioTrack?.stop()
//        audioTrack?.dispose()
//        if (::room.isInitialized) {
//            room.disconnect()
//        }
//        Log.d("LIVEKIT", "🛑 Сервис завершён")
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    private fun startForegroundService() {
//        val channelId = "audio_stream_channel"
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                channelId,
//                "Audio Stream",
//                NotificationManager.IMPORTANCE_LOW
//            ).apply {
//                description = "LiveKit аудиотрансляция"
//            }
//            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
//        }
//
//        val notification = NotificationCompat.Builder(this, channelId)
//            .setContentTitle("SafeOrbit")
//            .setContentText("LiveKit аудиотрансляция активна")
//            .setSmallIcon(R.drawable.ic_mic)
//            .build()
//
//        startForeground(2, notification)
//    }
//}
