package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.LocationData
import kotlin.math.pow
import java.util.Timer
import java.util.TimerTask
import kotlin.math.sqrt
import kotlin.math.abs

@AndroidEntryPoint
class LocationService : Service(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var lastAcceleration = 0f
    private var moving = false

    private val viewModel by lazy {
        ServerViewModel(application)
    }

    private var timer: Timer? = null
    private val updateIntervalMoving = 30_000L     // 30 секунд
    private val updateIntervalStationary = 10 * 60_000L // 10 минут

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        startForegroundWithNotification()
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )

        scheduleLocationUpdates()
    }

    private fun startForegroundWithNotification() {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Location Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Отслеживание координат")
            .setContentText("Приложение отправляет координаты")
            .setSmallIcon(R.drawable.ic_location)
            .build()

        startForeground(1, notification)
    }

    private fun scheduleLocationUpdates() {
        timer?.cancel()
        timer = Timer()
        val interval = if (moving) updateIntervalMoving else updateIntervalStationary

        timer?.schedule(object : TimerTask() {
            override fun run() {
                sendLocation()
            }
        }, 0, interval)
    }

    private fun sendLocation() {
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
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val data = LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
                viewModel.sendLocation(data)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val acceleration = sqrt(it.values[0].pow(2) + it.values[1].pow(2) + it.values[2].pow(2))
            val delta = abs(acceleration - lastAcceleration)
            lastAcceleration = acceleration

            val isNowMoving = delta > 0.5f
            if (isNowMoving != moving) {
                moving = isNowMoving
                scheduleLocationUpdates()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        sensorManager.unregisterListener(this)
    }
}
