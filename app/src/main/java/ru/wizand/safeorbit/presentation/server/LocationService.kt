package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ActivityLogEntity
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service(), SensorEventListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager

    private var serverId: String = ""
    private var isInActiveMode: Boolean = false
    private var lastStepTime: Long = 0L
    private var lastSentTime: Long = 0L
    private var lastSentLocation: Location? = null

    private val ACTIVE_INTERVAL = 30_000L
    private var inactivityTimeout: Long = 5 * 60 * 1000L

    private var initialStepCount: Float? = null
    private var lastStepCount: Float = 0f
    private var lastStepEventTime: Long = 0L

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "inactivity_timeout") {
            val newTimeout = prefs.getLong("inactivity_timeout", inactivityTimeout)
            Log.d("LocationService", "🔄 inactivity_timeout обновлён: $inactivityTimeout → $newTimeout")
            inactivityTimeout = newTimeout
            if (!isInActiveMode) {
                switchToIdleMode()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        inactivityTimeout = prefs.getLong("inactivity_timeout", inactivityTimeout)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        startForegroundWithNotification()
        setupStepSensor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverId = intent?.getStringExtra("server_id") ?: ""

        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        switchToIdleMode()
        return START_STICKY
    }

    private fun setupStepSensor() {
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Log.w("LocationService", "Датчик шагов недоступен")
        }
    }

    private fun startLocationUpdates(interval: Long) {
        locationRequest = LocationRequest.Builder(interval)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleLocationUpdate(location)
            }
        }

        if (hasLocationPermission()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
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
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            Log.d("LocationService", "📡 FusedLocation: Запрошены обновления каждые $interval мс")
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationService", "🛑 FusedLocation: обновления остановлены")
        } else {
            Log.w("LocationService", "⚠️ stopLocationUpdates: callback ещё не инициализирован")
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val now = System.currentTimeMillis()
        val isFirst = lastSentLocation == null
        val timeSinceLast = now - lastSentTime
        val distanceMoved = lastSentLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val stepRecently = now - lastStepTime < 2 * 60 * 1000L

        val isActive = distanceMoved > 50f || stepRecently
        val isDueByTime = timeSinceLast > inactivityTimeout
        val shouldSend = isFirst || isActive || isDueByTime

        Log.d("LocationService", "📍 Координаты: ${location.latitude}, ${location.longitude}, Δ=$distanceMoved, шаги недавно=$stepRecently")

        if (shouldSend) {
            lastSentLocation = location
            lastSentTime = now
            broadcastLocation(location)

            // ➕ Отправка в Firebase
            val repo = FirebaseRepository(applicationContext)
            val locationData = ru.wizand.safeorbit.data.model.LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )
            repo.sendLocation(serverId, locationData)
            Log.d("LocationService", "📤 Координаты отправлены в Firebase: $locationData")
        }

        if (isActive && !isInActiveMode) {
            switchToActiveMode()
        } else if (!stepRecently && isInActiveMode && timeSinceLast > inactivityTimeout) {
            switchToIdleMode()
        }
    }

    private fun switchToActiveMode() {
        Log.d("LocationService", "🔁 Переход в АКТИВНЫЙ режим")
        isInActiveMode = true
        stopLocationUpdates()
        startLocationUpdates(ACTIVE_INTERVAL)
        broadcastMode()
    }

    private fun switchToIdleMode() {
        Log.d("LocationService", "🔁 Переход в ЭКОНОМ режим (таймаут: $inactivityTimeout мс)")
        isInActiveMode = false
        stopLocationUpdates()
        startLocationUpdates(inactivityTimeout)
        broadcastMode()
    }

    private fun broadcastLocation(location: Location) {
        val intent = Intent("LOCATION_UPDATE").apply {
            putExtra("latitude", location.latitude)
            putExtra("longitude", location.longitude)
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("mode", if (isInActiveMode) "АКТИВНЫЙ" else "ЭКОНОМ")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("LocationService", "📡 Отправка координат")
    }

    private fun broadcastMode() {
        val intent = Intent("LOCATION_UPDATE").apply {
            putExtra("mode", if (isInActiveMode) "АКТИВНЫЙ" else "ЭКОНОМ")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("LocationService", "📡 Обновление режима: ${if (isInActiveMode) "АКТИВНЫЙ" else "ЭКОНОМ"}")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values.firstOrNull() ?: return
            if (initialStepCount == null) {
                initialStepCount = totalSteps
                lastStepCount = totalSteps
                Log.d("LocationService", "👟 Step sensor инициализирован: $totalSteps")
                return
            }

            val delta = totalSteps - lastStepCount
            if (delta >= 2f) {
                lastStepCount = totalSteps
                lastStepTime = System.currentTimeMillis()
                Log.d("LocationService", "👟 Реальные шаги: +$delta, всего: $totalSteps")
            }

            val timeSinceLast = System.currentTimeMillis() - lastStepEventTime
            if (timeSinceLast < 2000L) {
                Log.d("LocationService", "👟 Игнор. шаг — слишком часто ($timeSinceLast мс)")
                return
            }
            lastStepEventTime = System.currentTimeMillis()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startForegroundWithNotification() {
        val channelId = "location_service_channel"
        val channelName = "Location Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeOrbit")
            .setContentText("Отслеживание местоположения включено")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val fg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        else PackageManager.PERMISSION_GRANTED

        return fine == PackageManager.PERMISSION_GRANTED && fg == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
