package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.google.android.gms.location.*
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.worker.SendLocationWorker
import ru.wizand.safeorbit.utils.LocationLogger
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var activityPendingIntent: PendingIntent

    private var serverId: String? = null
    private var lastSentLocation: Location? = null
    private var isMoving = false

    private val updateIntervalMoving = 30_000L // интервал GPS обновлений при движении
    private val minDistanceToSend = 50 // минимальное расстояние (в метрах) для отправки новой точки

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)

        startForegroundWithNotification()

        // Обработка полученных координат
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                LocationLogger.debug("Получена локация: $location")
                location?.let { handleNewLocation(it) }
            }
        }

        // PendingIntent, который будет вызываться системой при смене активности
        val intent = Intent(this, ActivityReceiver::class.java)
        activityPendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Запрашиваем отслеживание активности (ходьба, покой)
        requestActivityUpdates()

        LocationLogger.info("Сервис создан")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Если пришёл интент от ActivityReceiver — обрабатываем его
        val activityType = intent?.getIntExtra("activity_type", -1)
        val transitionType = intent?.getIntExtra("transition_type", -1)

        if (activityType != null && transitionType != null && activityType != -1) {
            LocationLogger.debug("ActivityReceiver: type=$activityType, transition=$transitionType")

            when (activityType) {
                DetectedActivity.WALKING -> {
                    if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER && !isMoving) {
                        isMoving = true
                        startLocationUpdates()
                        LocationLogger.debug("Ходьба началась — включаем GPS")
                    } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                        isMoving = false
                        stopLocationUpdates()
                        LocationLogger.debug("Ходьба завершилась — отключаем GPS")
                    }
                }

                DetectedActivity.STILL -> {
                    if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        isMoving = false
                        stopLocationUpdates()
                        LocationLogger.debug("Покой — отключаем GPS")
                    }
                }
            }
        }

        // Получаем serverId (передан в интенте или уже был сохранён)
        serverId = intent?.getStringExtra("server_id") ?: serverId

        if (serverId.isNullOrEmpty()) {
            LocationLogger.warn("Server ID отсутствует — сервис остановлен")
            stopSelf()
        } else {
            LocationLogger.debug("Сервис запущен с serverId = $serverId")
        }

        // ❗ Немедленная попытка получить последнюю известную локацию
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && serverId != null) {
                    LocationLogger.info("Первичная локация получена: $location")
                    handleNewLocation(location)
                } else {
                    LocationLogger.warn("Не удалось получить первичную локацию")
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Location Tracking", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            LocationLogger.warn("Нет разрешения ACTIVITY_RECOGNITION")
            return
        }

        activityRecognitionClient
            .requestActivityTransitionUpdates(request, activityPendingIntent)
            .addOnSuccessListener {
                LocationLogger.info("Мониторинг активности запущен")
            }
            .addOnFailureListener {
                LocationLogger.error("Ошибка запуска мониторинга активности", it)
            }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            LocationLogger.warn("Нет разрешения на геолокацию")
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            updateIntervalMoving
        ).setMinUpdateDistanceMeters(10f).build()

        fusedLocationClient.requestLocationUpdates(
            request, locationCallback, Looper.getMainLooper()
        )
        LocationLogger.debug("Старт GPS обновлений")
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        LocationLogger.debug("Остановка GPS обновлений")
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
                putExtra("timestamp", data.timestamp)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

            LocationLogger.info("Локация отправлена: $data")
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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        LocationLogger.debug("Создан WorkManager task для отправки локации")
    }

    override fun onDestroy() {
        stopLocationUpdates()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            activityRecognitionClient.removeActivityTransitionUpdates(activityPendingIntent)
        }

        LocationLogger.warn("Сервис уничтожен")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
