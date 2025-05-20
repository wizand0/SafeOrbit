package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.*
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service(), SensorEventListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager

    private lateinit var db: AppDatabase
    private lateinit var logDao: ActivityLogDao

    private var serverId: String = ""
    private var isInActiveMode = false
    private var lastStepTime = 0L
    private var lastSentTime = 0L
    private var lastSentLocation: Location? = null

    private var initialStepCount: Float? = null
    private var lastStepCount = 0f
    private var lastStepEventTime = 0L

    private var activeInterval = 30_000L
    private var inactivityTimeout = 5 * 60 * 1000L

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        inactivityTimeout = prefs.getLong("inactivity_timeout", inactivityTimeout)
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "inactivity_timeout") {
                inactivityTimeout = prefs.getLong(key, inactivityTimeout)
                if (!isInActiveMode) switchToIdleMode()
            }
        }

        activeInterval = prefs.getLong("active_interval", activeInterval)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "safeorbit-db").build()
        logDao = db.activityLogDao()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        startForegroundService()
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
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun startLocationUpdates(interval: Long) {
        val request = LocationRequest.Builder(interval)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { handleLocationUpdate(it) }
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
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val now = System.currentTimeMillis()
        val isFirst = lastSentLocation == null
        val timeSinceLast = now - lastSentTime
        val distanceMoved = lastSentLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val stepRecently = now - lastStepTime < 2 * 60 * 1000L

        val isActive = distanceMoved > 50f || stepRecently
        val shouldSend = isFirst || isActive || timeSinceLast > inactivityTimeout

        if (shouldSend) {
            lastSentLocation = location
            lastSentTime = now
            sendToFirebase(location)
            broadcastLocation(location)
            saveActivityLog(if (isActive) "Активность" else "ЭКОНОМ")
        }

        if (isActive && !isInActiveMode) switchToActiveMode()
        else if (!stepRecently && isInActiveMode && timeSinceLast > inactivityTimeout) switchToIdleMode()
    }

    private fun sendToFirebase(location: Location) {
        FirebaseRepository(applicationContext).sendLocation(
            serverId,
            LocationData(location.latitude, location.longitude, System.currentTimeMillis())
        )
    }

    private fun switchToActiveMode() {
        isInActiveMode = true
        stopLocationUpdates()
        startLocationUpdates(activeInterval)
        broadcastMode()
    }

    private fun switchToIdleMode() {
        isInActiveMode = false
        stopLocationUpdates()
        startLocationUpdates(inactivityTimeout)
        broadcastMode()
    }

    private fun broadcastLocation(location: Location) {
        Intent("LOCATION_UPDATE").apply {
            putExtra("latitude", location.latitude)
            putExtra("longitude", location.longitude)
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("mode", if (isInActiveMode) "АКТИВНЫЙ" else "ЭКОНОМ")
        }.also {
            LocalBroadcastManager.getInstance(this).sendBroadcast(it)
        }
    }

    private fun broadcastMode() {
        Intent("LOCATION_UPDATE").apply {
            putExtra("mode", if (isInActiveMode) "АКТИВНЫЙ" else "ЭКОНОМ")
        }.also {
            LocalBroadcastManager.getInstance(this).sendBroadcast(it)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentSteps = event.values.firstOrNull() ?: return
            if (initialStepCount == null) initialStepCount = currentSteps
            val delta = currentSteps - lastStepCount
            if (delta >= 2f) {
                lastStepCount = currentSteps
                lastStepTime = System.currentTimeMillis()
            }
            val now = System.currentTimeMillis()
            if (now - lastStepEventTime >= 2000L) {
                lastStepEventTime = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveActivityLog(mode: String) {
        val now = Calendar.getInstance()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val steps = if (mode == "Активность" && initialStepCount != null) {
            (lastStepCount - initialStepCount!!).toInt().coerceAtLeast(0)
        } else null

        val location = lastSentLocation
        val distance: Float? = if (mode == "Активность" && location != null) {
            location.speed.takeIf { it > 0 }?.times(inactivityTimeout / 1000f)
        } else null

        val log = ActivityLogEntity(
            date = date,
            startHour = hour,
            endHour = (hour + 1).coerceAtMost(24),
            mode = mode,
            steps = steps,
            distanceMeters = distance
        )

        CoroutineScope(Dispatchers.IO).launch {
            logDao.insert(log)
        }
    }

    private fun startForegroundService() {
        val channelId = "location_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Location Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafeOrbit")
            .setContentText("Отслеживание местоположения включено")
            .setSmallIcon(R.drawable.ic_location)
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
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
        prefs.unregisterOnSharedPreferenceChangeListener { _, _ -> }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
