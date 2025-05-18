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
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ActivityLogEntity
import ru.wizand.safeorbit.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationService : Service(), LocationListener, SensorEventListener {

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var prefs: SharedPreferences

    private var serverId: String = ""
    private var lastSentTime: Long = 0L
    private var lastSentLocation: Location? = null
    private var lastStepTime: Long = 0L
    private var isInActiveMode: Boolean = false

    private var initialStepCount: Float? = null
    private var lastStepCount: Float = 0f
    private var lastStepEventTime: Long = 0L

    private val ACTIVE_INTERVAL = 30_000L // 30 сек
    private var inactivityTimeout: Long = 5 * 60 * 1000L // default 5 минут

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

    private fun loadSettings() {
        inactivityTimeout = prefs.getLong("inactivity_timeout", 5 * 60 * 1000L)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        loadSettings()
        startForegroundWithNotification()
        setupStepSensor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverId = intent?.getStringExtra("server_id") ?: ""

        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        switchToIdleMode()
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val now = System.currentTimeMillis()
        val isFirst = lastSentLocation == null
        val timeSinceLast = now - lastSentTime
        val distanceMoved = lastSentLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val stepRecently = now - lastStepTime < 2 * 60 * 1000L

        val isActive = distanceMoved > 50f || stepRecently
        val isDueByTime = timeSinceLast > inactivityTimeout
        val shouldSend = isFirst || isActive || isDueByTime

        Log.d("LocationService", "📍 Location: ${location.latitude}, ${location.longitude}, Δ=$distanceMoved m, steps recent=$stepRecently")

        if (shouldSend) {
            lastSentLocation = location
            lastSentTime = now
            broadcastLocation(location)
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
        try {
            locationManager.removeUpdates(this)
            if (!hasLocationPermission()) return
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
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                ACTIVE_INTERVAL,
                0f,
                this
            )
        } catch (e: Exception) {
            Log.e("LocationService", "Ошибка переключения в активный режим: ${e.message}")
        }
        broadcastMode()
    }

    private fun switchToIdleMode() {
        loadSettings()
        Log.d("LocationService", "🔁 Переход в ЭКОНОМ режим (таймаут: $inactivityTimeout мс)")
        isInActiveMode = false
        try {
            locationManager.removeUpdates(this)
            if (!hasLocationPermission()) return
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
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                inactivityTimeout,
                0f,
                this
            )
        } catch (e: Exception) {
            Log.e("LocationService", "Ошибка переключения в idle режим: ${e.message}")
        }
        broadcastMode()
    }

    private fun setupStepSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Log.w("LocationService", "Датчик шагов недоступен")
        }
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
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        val fg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        else PackageManager.PERMISSION_GRANTED

        return fine == PackageManager.PERMISSION_GRANTED &&
                (coarse == PackageManager.PERMISSION_GRANTED || fine == PackageManager.PERMISSION_GRANTED) &&
                fg == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        try {
            locationManager.removeUpdates(this)
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.w("LocationService", "Ошибка при остановке: ${e.message}")
        }
    }

    private suspend fun saveActivityLog(
        context: Context,
        startHour: Int,
        endHour: Int,
        steps: Int,
        distance: Float,
        isActive: Boolean
    ) {
        val mode = if (isActive) "Активность" else "ЭКОНОМ"
        val log = ActivityLogEntity(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            startHour = startHour,
            endHour = endHour,
            mode = mode,
            steps = if (isActive) steps else null,
            distanceMeters = if (isActive) distance else null
        )

        withContext(Dispatchers.IO) {
            val db = Room.databaseBuilder(
                context,
                AppDatabase::class.java, "safeorbit-db"
            ).build()
            db.activityLogDao().insert(log)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
