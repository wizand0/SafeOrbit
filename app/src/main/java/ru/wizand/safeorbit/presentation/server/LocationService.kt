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
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.*
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.*
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.presentation.server.audio.AudioBroadcastService
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
        val code = prefs.getString("server_code", "")
        Log.d("COMMANDS", "📦 Сервис запущен. serverId=$serverId, code=$code") // ⬅️ ДОБАВЬ

        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        listenForClientCommands()
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
        Log.d("COMMANDS", "📤 Отправка координат: ${location.latitude}, ${location.longitude}")
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
            putExtra("active_interval", activeInterval)
            putExtra("inactivity_timeout", inactivityTimeout)
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

    private fun listenForClientCommands() {
        val commandRootRef = FirebaseDatabase.getInstance()
            .getReference("server_commands")
            .child(serverId)

        Log.d("COMMANDS", "📛 Firebase UID: ${FirebaseAuth.getInstance().currentUser?.uid}, serverId: $serverId")


        Log.d("COMMANDS", "⏳ Слушаем команды на server_commands/$serverId")

        commandRootRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val commandId = snapshot.key ?: return
                val codeFromClient = snapshot.child("code").getValue(String::class.java)
                val localCode = prefs.getString("server_code", "")

                Log.d("COMMANDS", "📥 Получена команда $commandId: ${snapshot.value}")
                Log.d("COMMANDS", "🔐 Проверка кода: client=$codeFromClient, local=$localCode")

                if (codeFromClient != localCode) {
                    Log.w("COMMANDS", "❌ Код не совпадает — игнорируем")
                    return
                }

                val active = snapshot.child("update_settings/active_interval").getValue(Long::class.java)
                val idle = snapshot.child("update_settings/inactivity_timeout").getValue(Long::class.java)
                val requestNow = snapshot.child("request_location_update").getValue(Boolean::class.java) ?: false

                if (active != null) {
                    Log.d("COMMANDS", "⚙️ Установка activeInterval = $active")
                    activeInterval = active
                    prefs.edit().putLong("active_interval", active).apply()
                    if (isInActiveMode) switchToActiveMode()
                }

                if (idle != null) {
                    Log.d("COMMANDS", "⚙️ Установка inactivityTimeout = $idle")
                    inactivityTimeout = idle
                    prefs.edit().putLong("inactivity_timeout", idle).apply()
                    if (!isInActiveMode) switchToIdleMode()
                }

                broadcastMode()

                if (requestNow) {
                    Log.d("COMMANDS", "📡 Принудительная отправка координат")

                    if (hasLocationPermission()) {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                if (location != null) {
                                    lastSentLocation = location
                                    lastSentTime = System.currentTimeMillis()
                                    sendToFirebase(location)
                                    broadcastLocation(location)
                                    saveActivityLog("Принудительно")
                                    Log.d("COMMANDS", "📤 Отправлены координаты: ${location.latitude}, ${location.longitude}")
                                } else {
                                    Log.w("COMMANDS", "⚠️ Не удалось получить текущую локацию")
                                }
                            }
                            .addOnFailureListener {
                                Log.e("COMMANDS", "❌ Ошибка при получении локации: ${it.message}")
                            }
                    } else {
                        Log.w("COMMANDS", "⚠️ Нет разрешения на получение локации")
                    }
                }

                val commandType = snapshot.child("type").getValue(String::class.java)

                when (commandType) {
                    "START_AUDIO_STREAM" -> {
                        Log.d("COMMANDS", "🎙️ Команда на запуск аудио трансляции")
                        startAudioBroadcastService()
                    }
                    "STOP_AUDIO_STREAM" -> {
                        Log.d("COMMANDS", "🛑 Команда на остановку аудио трансляции")
                        stopAudioBroadcastService()
                    }
                }

                // 🧹 Удаление команды
                snapshot.ref.removeValue()
                Log.d("COMMANDS", "🧹 Команда $commandId удалена после обработки")
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("COMMANDS", "🔥 Ошибка чтения команд: ${error.message}")
            }
        })
    }


    private fun processCommandSnapshot(snapshot: DataSnapshot) {
        val parentRef = snapshot.ref.parent ?: return

        parentRef.get().addOnSuccessListener { snapshot ->
            Log.d("COMMANDS", "📥 Получена команда: ${snapshot.value}")

            val codeFromClient = snapshot.child("code").getValue(String::class.java)
            val localCode = prefs.getString("server_code", "")
            Log.d("COMMANDS", "🔐 Проверка кода: client=$codeFromClient, local=$localCode")

            if (codeFromClient != localCode) {
                Log.w("COMMANDS", "❌ Код не совпадает — игнорируем")
                return@addOnSuccessListener
            }

            val active = snapshot.child("update_settings/active_interval").getValue(Long::class.java)
            val idle = snapshot.child("update_settings/inactivity_timeout").getValue(Long::class.java)
            val requestNow = snapshot.child("request_location_update").getValue(Boolean::class.java) ?: false

            if (active != null) {
                Log.d("COMMANDS", "⚙️ Установка activeInterval = $active")
                activeInterval = active
                if (isInActiveMode) switchToActiveMode()
            }

            if (idle != null) {
                Log.d("COMMANDS", "⚙️ Установка inactivityTimeout = $idle")
                inactivityTimeout = idle
                if (!isInActiveMode) switchToIdleMode()
            }

            if (requestNow) {
                Log.d("COMMANDS", "📡 Принудительная отправка координат")
                lastSentLocation?.let { sendToFirebase(it) }
            }

            parentRef.removeValue()
            Log.d("COMMANDS", "🧹 Команда обработана и удалена")
        }.addOnFailureListener {
            Log.e("COMMANDS", "⚠️ Не удалось прочитать команду: ${it.message}")
        }
    }

//    private fun startAudioBroadcastService() {
//        val intent = Intent(this, ru.wizand.safeorbit.presentation.server.audio.SilentAudioLaunchActivity::class.java)
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        intent.putExtra("server_id", serverId)
//        startActivity(intent)
//        Log.d("COMMANDS", "🎤 SilentAudioLaunchActivity запущена для старта AudioBroadcastService")
//    }

    private fun startAudioBroadcastService() {
        val intent = Intent(this, AudioBroadcastService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Log.d("COMMANDS", "🎤 SilentAudioLaunchActivity запущена для старта AudioBroadcastService")
    }

    private fun stopAudioBroadcastService() {
        val intent = Intent(this, AudioBroadcastService::class.java)
        stopService(intent)
        Log.d("COMMANDS", "🛑 AudioBroadcastService остановлен")
    }



    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
        prefs.unregisterOnSharedPreferenceChangeListener { _, _ -> }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}