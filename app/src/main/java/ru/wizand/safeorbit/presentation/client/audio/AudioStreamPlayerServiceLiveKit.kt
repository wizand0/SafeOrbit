//package ru.wizand.safeorbit.presentation.client.audio
//
//import android.app.*
//import android.content.Intent
//import android.os.*
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import io.livekit.android.LiveKit
//import io.livekit.android.room.Room
//import io.livekit.android.events.RoomEvent
//import io.livekit.android.events.collect
//import io.livekit.android.room.participant.RemoteParticipant
//import io.livekit.android.room.track.RemoteAudioTrack
//import kotlinx.coroutines.*
//
//class AudioStreamPlayerServiceLiveKit : Service() {
//
//    private lateinit var room: Room
//    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
//
//    private val livekitUrl = "wss://safeosint-vp0jv9bs.livekit.cloud" // Твой URL
//    private val token = "epph9iCyFQut03e1cuzBm2fHpwo4q0mF2L8Zao5Y9gvB" // 🎯 Подставь сгенерированный токен (identity может быть random UUID)
//
//
//
//    override fun onCreate() {
//        super.onCreate()
//        startForegroundService()
//        connectToLiveKit()
//    }
//
//    private fun connectToLiveKit() {
//        coroutineScope.launch {
//            try {
//                room = LiveKit.create(applicationContext)
//                room.connect(livekitUrl, token)
//
//                launch {
//                    room.events.collect { event ->
//                        when (event) {
//                            is RoomEvent.TrackSubscribed -> {
//                                val track = event.track
//                                val participant = event.participant
//                                if (track is RemoteAudioTrack && participant is RemoteParticipant) {
//                                    Log.d("AUDIO_CLIENT", "🎧 Аудио-трек получен от ${participant.identity}")
//                                    track.start()
//                                }
//                            }
//                            is RoomEvent.Disconnected -> {
//                                Log.d("AUDIO_CLIENT", "🔌 Отключено от комнаты")
//                                stopSelf()
//                            }
//                            else -> {
//                                Log.d("AUDIO_CLIENT", "Событие: ${event::class.simpleName}")
//                            }
//                        }
//                    }
//                }
//
//            } catch (e: Exception) {
//                Log.e("AUDIO_CLIENT", "❌ Ошибка подключения к LiveKit: ${e.message}")
//                stopSelf()
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        coroutineScope.cancel()
//        if (::room.isInitialized) room.disconnect()
//        Log.d("AUDIO_CLIENT", "🛑 Сервис завершён")
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    private fun startForegroundService() {
//        val channelId = "audio_playback_channel"
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                channelId,
//                "Audio Playback",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
//        }
//
//        val notification = NotificationCompat.Builder(this, channelId)
//            .setContentTitle("SafeOrbit")
//            .setContentText("Прослушивание аудиотрансляции (LiveKit)")
//            .setSmallIcon(ru.wizand.safeorbit.R.drawable.ic_mic)
//            .build()
//
//        startForeground(3, notification)
//    }
//}
