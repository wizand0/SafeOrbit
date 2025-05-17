package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.google.android.gms.location.*
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.worker.SendLocationWorker
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var activityPendingIntent: PendingIntent

    private var serverId: String? = null
    private var lastSentLocation: Location? = null
    private var isMoving = false

    private val updateIntervalMoving = 30_000L // 30 секунд
    private val minDistanceToSend = 50 // метров

    override fun onCreate() {
        super.onCreate()

        // Инициализация клиентов
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)

        // Уведомление foreground-сервиса
        startForegroundWithNotification()

        // Callback при получении локации
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { handleNewLocation(it) }
            }
        }

        // Подготовка интента и PendingIntent для мониторинга активности
        val intent = Intent("ACTIVITY_RECOGNIZED")
        activityPendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Регистрируем broadcast receiver для активности
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                activityReceiver,
                IntentFilter("ACTIVITY_RECOGNIZED"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(activityReceiver, IntentFilter("ACTIVITY_RECOGNIZED"))
        }

        // Запускаем мониторинг активности
        requestActivityUpdates()

        Log.d("LOCATION_SERVICE", "Сервис создан")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverId = intent?.getStringExtra("server_id")

        if (serverId.isNullOrEmpty()) {
            stopSelf()
        } else {
            Log.d("LOCATION_SERVICE", "Сервис запущен с serverId = $serverId")
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Отслеживание координат")
            .setContentText("Приложение отправляет координаты")
            .setSmallIcon(R.drawable.ic_location)
            .build()

        startForeground(1, notification)
    }

    private fun requestActivityUpdates() {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )

        val request = ActivityTransitionRequest(transitions)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        activityRecognitionClient
            .requestActivityTransitionUpdates(request, activityPendingIntent)
            .addOnSuccessListener {
                Log.d("ACTIVITY_RECOGNITION", "Мониторинг активности запущен")
            }
            .addOnFailureListener {
                Log.e("ACTIVITY_RECOGNITION", "Ошибка запуска мониторинга: ${it.message}")
            }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            updateIntervalMoving
        ).setMinUpdateDistanceMeters(10f)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun handleNewLocation(location: Location) {
        if (serverId.isNullOrEmpty()) return

        val shouldSend = lastSentLocation?.let {
            location.distanceTo(it) > minDistanceToSend
        } ?: true

        if (shouldSend) {
            lastSentLocation = location

            val data = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )

            enqueueSendLocationWorker(serverId!!, data)

            val intent = Intent("LOCATION_UPDATE").apply {
                putExtra("latitude", data.latitude)
                putExtra("longitude", data.longitude)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            Log.d("SEND_LOCATION", "Отправлено: $data")
        }
    }

    private fun enqueueSendLocationWorker(serverId: String, data: LocationData) {
        val workRequest = OneTimeWorkRequestBuilder<SendLocationWorker>()
            .setInputData(
                Data.Builder()
                    .putString("server_id", serverId)
                    .putDouble("latitude", data.latitude)
                    .putDouble("longitude", data.longitude)
                    .putLong("timestamp", data.timestamp)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val transitionResult = ActivityTransitionResult.extractResult(intent!!) ?: return

            for (event in transitionResult.transitionEvents) {
                when (event.activityType) {
                    DetectedActivity.WALKING -> {
                        if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                            if (!isMoving) {
                                isMoving = true
                                startLocationUpdates()
                                Log.d("ACTIVITY", "Движение началось — включаем GPS")
                            }
                        } else {
                            isMoving = false
                            stopLocationUpdates()
                            Log.d("ACTIVITY", "Движение завершено — отключаем GPS")
                        }
                    }

                    DetectedActivity.STILL -> {
                        if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                            isMoving = false
                            stopLocationUpdates()
                            Log.d("ACTIVITY", "Покой — отключаем GPS")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(activityReceiver)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        activityRecognitionClient.removeActivityTransitionUpdates(activityPendingIntent)
        stopLocationUpdates()
        Log.d("LOCATION_SERVICE", "Сервис уничтожен")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
