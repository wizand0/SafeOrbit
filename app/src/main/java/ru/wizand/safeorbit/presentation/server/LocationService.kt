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
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.*
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager
import ru.wizand.safeorbit.presentation.server.worker.IdleLocationWorker
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ФИКСЫ (см. аудит):
 * 1.1 — именованный prefsListener вместо анонимных лямбд в register/unregister
 * 1.2 — Room DB больше не создаётся вручную, инжектится Hilt-синглтон
 * 1.3 — serviceScope с SupervisorJob вместо CoroutineScope(Dispatchers.IO) на каждый вызов
 * 1.4 — убран дублирующийся безусловный postStart(); WorkManager unique work вместо enqueue()
 * 1.6 — FirebaseRepository инжектится как синглтон, а не создаётся на каждый вызов
 */
@AndroidEntryPoint
class LocationService : Service(), SensorEventListener {

    // --- 1.2 / 1.6: инжектируем синглтоны через Hilt вместо ручного создания ---
    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private lateinit var prefs: SharedPreferences
    private lateinit var encryptedPrefs: EncryptedPreferencesManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager

    private lateinit var logDao: ActivityLogDao

    private var serverId: String = ""
    private var isInActiveMode = false
    private var lastStepTime = 0L
    private var lastSentTime = 0L
    private var lastSentLocation: Location? = null

    private var initialStepCount: Float? = null
    private var lastStepCount = 0f
    private var lastStepEventTime = 0L

    // 2.2 (аудит): гистерезис активности — в активный режим переходим только после
    // двух подряд "активных" фиксов, чтобы единичный скачок координат (дрейф GPS)
    // не будил GPS-трекинг из эконом-режима.
    private var consecutiveActiveFixes = 0

    // --- FIX (петля команд): id уже обработанных команд этой подписки.
    // Без него откат неудалившегося removeValue() переизлучает onChildAdded
    // и сервер заново исполняет ту же команду (бесконечная отправка координат).
    private val processedCommands = mutableSetOf<String>()

    // Команды старше этого возраста считаются устаревшими: после переподписки
    // / рестарта сервиса накопленный бэклог не должен исполняться как «свежий».
    private val commandTtlMs = 2 * 60 * 1000L

    private var commandListener: ChildEventListener? = null

    private var activeInterval = 30_000L
    private var inactivityTimeout = 5 * 60 * 1000L

    // --- 1.3: единый scope сервиса, отменяется целиком в onDestroy ---
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- УведомлениеForeground-сервиса: id/канал + подсветка момента отправки ---
    private companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_FLASH_MS = 8_000L
    }

    private var notificationFlashJob: Job? = null

    // Реалная отправка координат в эко-режиме идёт из IdleLocationWorker (не через
    // LocationService) — worker сигнализирует об этом локальным broadcast'ом,
    // чтобы уведомление подсветило момент обновления.
    private val idleSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            flashLocationSent()
        }
    }

    // --- 1.1: именованный листенер, чтобы register/unregister ссылались на один и тот же объект ---
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "inactivity_timeout") {
            inactivityTimeout = prefs.getLong(key, inactivityTimeout)
            if (!isInActiveMode) switchToIdleMode()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        encryptedPrefs = EncryptedPreferencesManager.getInstance(applicationContext)

        val role = encryptedPrefs.getUserRole()
        if (role != UserRole.SERVER.name) {
            Log.w("LocationService", "❌ Неверная роль: $role. Сервис не запущен.")
            stopSelf()
            return
        }

        if (!hasLocationPermission()) {
            Log.w("LocationService", "❌ Нет разрешений. Сервис не запущен.")
            stopSelf()
            return
        }

        inactivityTimeout = prefs.getLong("inactivity_timeout", inactivityTimeout)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        activeInterval = prefs.getLong("active_interval", activeInterval)

        // 1.2: db приходит уже готовым от Hilt (тот же синглтон, что и везде в приложении)
        logDao = db.activityLogDao()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(idleSentReceiver, IntentFilter("IDLE_LOCATION_SENT"))

        startForegroundService()
        setupStepSensor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Фикс (аудит): после перезагрузки BootReceiver стартует сервис без extra —
        // ранее serverId оставался пустым и сервис подписывался на server_commands/"".
        serverId = intent?.getStringExtra("server_id")?.takeIf { it.isNotBlank() }
            ?: encryptedPrefs.getServerId().orEmpty()
        Log.d("COMMANDS", "📦 Сервис запущен. serverId=$serverId")

        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1.4: убран безусловный postStart() перед проверкой авторизации —
        // теперь postStart() вызывается РОВНО один раз, в зависимости от состояния auth.
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            authenticateWithStoredCredentials(auth)
        } else {
            postStart()
        }

        return START_STICKY
    }

    private fun authenticateWithStoredCredentials(auth: FirebaseAuth) {
        val storedUid = encryptedPrefs.getAnonymousUid()
        if (!storedUid.isNullOrBlank()) {
            // Пытаемся использовать сохранённый UID для аутентификации
            // Проверяем, является ли текущий анонимный пользователь тем же, что и сохранённый
            auth.signInAnonymously()
                .addOnSuccessListener { authResult ->
                    val currentUid = authResult.user?.uid
                    if (currentUid == storedUid) {
                        Log.d("COMMANDS", "✅ Анонимная авторизация с совпадающим UID выполнена: $currentUid")
                        postStart()
                    } else {
                        Log.d("COMMANDS", "⚠️ UID не совпадает, используем текущий: $currentUid (ожидался: $storedUid)")
                        // Обновляем сохранённый UID, если новый пользователь анонимный
                        if (authResult.user?.isAnonymous == true && currentUid != null) {
                            encryptedPrefs.saveAnonymousUid(currentUid)
                            Log.d("COMMANDS", "🔄 Обновлён сохранённый UID анонимного пользователя")
                        }
                        postStart()
                    }
                }
                .addOnFailureListener {
                    Log.e("COMMANDS", "❌ Ошибка Firebase Auth: ${it.message}")
                    stopSelf()
                }
        } else {
            // Первичная анонимная аутентификация
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("COMMANDS", "✅ Анонимная авторизация Firebase выполнена")
                    // Сохраняем UID нового анонимного пользователя
                    val currentUid = auth.currentUser?.uid
                    if (!currentUid.isNullOrBlank()) {
                        encryptedPrefs.saveAnonymousUid(currentUid)
                        Log.d("COMMANDS", "💾 Сохранён новый UID анонимного пользователя: $currentUid")
                    }
                    postStart()
                }
                .addOnFailureListener {
                    Log.e("COMMANDS", "❌ Ошибка Firebase Auth: ${it.message}")
                    stopSelf()
                }
        }
    }

    private fun postStart() {
        Log.d("COMMANDS", "🚀 postStart вызван, активируем listener")
        listenForClientCommands()
        switchToIdleMode()
    }

    private fun setupStepSensor() {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun startLocationUpdates(interval: Long) {
        // 2.2 (аудит): минимальная дистанция 10 м отсекает пустые колбэки при стоянке;
        // waitForAccurateLocation=false — не ждём идеального фикса, берём что есть.
        val request = LocationRequest.Builder(interval)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateDistanceMeters(10f)
            .setWaitForAccurateLocation(false)
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

        // 2.2 (аудит): гистерезис — считаем подряд идущие активные фиксы
        if (isActive) consecutiveActiveFixes++ else consecutiveActiveFixes = 0

        if (consecutiveActiveFixes >= 2 && !isInActiveMode) switchToActiveMode()
        else if (!stepRecently && isInActiveMode && timeSinceLast > inactivityTimeout) switchToIdleMode()
    }

    private fun sendToFirebase(location: Location) {
        // 2.3 (аудит): координаты не печатаются в logcat
        Log.d("COMMANDS", "📤 Отправка координат")
        // 1.6: используем инжектированный синглтон вместо FirebaseRepository(applicationContext)
        firebaseRepository.sendLocation(
            serverId,
            LocationData(location.latitude, location.longitude, System.currentTimeMillis())
        )
        flashLocationSent()
    }

    private fun switchToActiveMode() {
        isInActiveMode = true
        stopLocationUpdates()
        if (activeInterval >= 30_000) {
            Log.d("COMMANDS", "📆 switchToActiveMode activeInterval >= 30_000")
            scheduleOneTimeLocationFetch(activeInterval)
        } else {
            Log.d("COMMANDS", "📆 switchToActiveMode activeInterval < 30_000")
            startLocationUpdates(activeInterval)
        }
        updateNotificationState()
        broadcastMode()
    }

    private fun switchToIdleMode() {
        isInActiveMode = false
        consecutiveActiveFixes = 0 // 2.2: сброс гистерезиса при уходе в эконом-режим
        stopLocationUpdates()
        Log.d("COMMANDS", "📆 switchToIdleMode")
        scheduleOneTimeLocationFetch(inactivityTimeout)
        updateNotificationState()
        broadcastMode()
    }

    // 1.4: unique work + REPLACE вместо enqueue() — старые ожидающие запросы отменяются,
    // а не накапливаются в очереди WorkManager.
    private fun scheduleOneTimeLocationFetch(interval: Long) {
        Log.d("COMMANDS", "📆 scheduleOneTimeLocationFetch Интервал: $interval мс")
        // 2.2 (аудит): не будим устройство при почти разряженной батарее —
        // воркер подождёт, пока заряд поднимется выше системного порога.
        val workRequest = OneTimeWorkRequestBuilder<IdleLocationWorker>()
            .setInitialDelay(interval, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this)
            .beginUniqueWork(
                "idle_location_fetch",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            .enqueue()

        Log.d("COMMANDS", "📆 IdleLocationWorker запланирован через ${interval}мс (unique='idle_location_fetch')")
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

        // 1.3: единый serviceScope с SupervisorJob, отменяется в onDestroy —
        // не остаётся "висящих" корутин после смерти сервиса.
        serviceScope.launch {
            logDao.insert(log)
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Отслеживание местоположения",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        // 2.3 (аудит): текст уведомления обязан раскрывать мониторинг (политика Play
        // User Trust / Spyware) — поэтому уведомление остаётся всегда, но отражает
        // реальное состояние: «ожидание» в эко-режиме и факт отправки в момент обновления.
        val notification = buildNotification(currentStateText())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): android.app.Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SafeOrbit")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_location)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** Текущий текст: эконом-режим или активное отслеживание. */
    private fun currentStateText(): String =
        if (isInActiveMode) "Координаты обновлены"
        else "Координаты: режим ожидания"

    private fun updateNotificationState() {
        notificationFlashJob?.cancel()
        notificationManager().notify(NOTIFICATION_ID, buildNotification(currentStateText()))
    }

    /**
     * Показывает «Координаты обновлены · HH:MM:SS» на время сразу после реальной
     * отправки, затем возвращает статусный текст режима.
     */
    private fun flashLocationSent() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        notificationFlashJob?.cancel()
        notificationManager().notify(
            NOTIFICATION_ID,
            buildNotification("Координаты обновлены · $time")
        )
        notificationFlashJob = serviceScope.launch {
            delay(NOTIFICATION_FLASH_MS)
            if (isActive) {
                notificationManager().notify(NOTIFICATION_ID, buildNotification(currentStateText()))
            }
        }
    }

    private fun notificationManager() =
        getSystemService(NotificationManager::class.java)

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val fg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        else PackageManager.PERMISSION_GRANTED
        return fine == PackageManager.PERMISSION_GRANTED && fg == PackageManager.PERMISSION_GRANTED
    }

    private fun listenForClientCommands() {
        commandListener?.let {
            FirebaseDatabase.getInstance()
                .getReference("server_commands")
                .child(serverId)
                .removeEventListener(it)
            commandListener = null
            Log.w("COMMANDS", "🔁 Повторная подписка: старый listener удалён")
        }
        processedCommands.clear()

        val commandRootRef = FirebaseDatabase.getInstance()
            .getReference("server_commands")
            .child(serverId)

        Log.d("COMMANDS", "🔔 Подписка на команды server_commands/$serverId")

        commandListener = object : ChildEventListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val commandId = snapshot.key ?: return
                if (!processedCommands.add(commandId)) {
                    // FIX: повторный emit той же команды (rollback после отказа в удалении)
                    Log.w("COMMANDS", "⏭ Команда $commandId уже обработана — пропуск (защита от петли)")
                    return
                }
                Log.d("COMMANDS", "📥 Команда получена: $commandId")

                val createdAt = snapshot.child("created_at").getValue(Long::class.java) ?: 0L
                val age = System.currentTimeMillis() - createdAt
                if (createdAt <= 0L || age > commandTtlMs) {
                    Log.w("COMMANDS", "🗑 Команда $commandId устарела (age=${age}мс) — удаляю без исполнения")
                    deleteCommand(snapshot.ref, commandId)
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
                                    Log.d("COMMANDS", "📤 Координаты отправлены")
                                } else {
                                    Log.w("COMMANDS", "⚠️ Не удалось получить локацию")
                                }
                            }
                            .addOnFailureListener {
                                Log.e("COMMANDS", "❌ Ошибка при получении локации: ${it.message}")
                            }
                    } else {
                        Log.w("COMMANDS", "⚠️ Нет разрешений на локацию")
                    }
                }

                deleteCommand(snapshot.ref, commandId)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                Log.e("COMMANDS", "🔥 Ошибка подписки на команды: ${error.message}")
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d("COMMANDS", "🔄 Повторная попытка подписки после onCancelled")
                    listenForClientCommands()
                }, 5000)
            }
        }

        try {
            commandRootRef.addChildEventListener(commandListener!!)
            Log.d("COMMANDS", "✅ Listener команд добавлен")
        } catch (e: Exception) {
            Log.e("COMMANDS", "🚨 Ошибка при добавлении listener: ${e.message}")
        }
    }

    /**
     * FIX: ack команды с явной обработкой ошибки.
     * Ранее removeValue() без колбэка при PERMISSION_DENIED молча откатывался,
     * откат переизлучал onChildAdded и сервер исполнял команду повторно.
     * Требуется правило server_commands/$serverId/$commandId .write для ownerUid (rules v3).
     * При отказе id остаётся в processedCommands — переизлучение команды игнорируется,
     * повтор подписки (listenForClientCommands) очистит множество и команда будет
     * обработана как новая только если не устарела по TTL.
     */
    private fun deleteCommand(ref: DatabaseReference, commandId: String) {
        ref.removeValue { error, _ ->
            if (error != null) {
                Log.e("COMMANDS", "❌ Не удалось удалить команду $commandId: ${error.code} ${error.message}")
            } else {
                Log.d("COMMANDS", "🧹 Команда $commandId удалена после обработки")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(idleSentReceiver)
        } catch (_: Exception) {
        }
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }

        // 1.1: отписываем ТОТ ЖЕ объект-листенер, что регистрировали в onCreate
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)

        commandListener?.let {
            FirebaseDatabase.getInstance()
                .getReference("server_commands")
                .child(serverId)
                .removeEventListener(it)
            Log.d("COMMANDS", "🧹 Listener команд удалён")
            commandListener = null
        }

        // 1.3: отменяем все незавершённые корутины сервиса разом
        serviceScope.cancel()

        // 1.2: db больше не создавалась вручную здесь — закрывать нечего,
        // это Hilt-синглтон, которым продолжает пользоваться остальное приложение.
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
