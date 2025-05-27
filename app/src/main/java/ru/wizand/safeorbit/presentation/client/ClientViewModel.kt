package ru.wizand.safeorbit.presentation.client

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.ServerEntity
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.utils.Event
import kotlin.math.*

data class ServerMapState(
    val latestPoint: Point,
    val history: List<Point>,
    val shouldCenter: Boolean,
    val color: Int,
    val timestamp: Long
)

class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application.applicationContext)
    private val db = AppDatabase.getDatabase(application)

    private var lastServerAudioCodeMap: MutableMap<String, String> = mutableMapOf()
    fun getAudioCodeFor(serverId: String): String? = lastServerAudioCodeMap[serverId]

    private var observingStarted = false

    private val _serverNameMap = MutableLiveData<Map<String, String>>()
    val serverNameMap: LiveData<Map<String, String>> = _serverNameMap

    private val _iconUriMap = MutableLiveData<Map<String, String?>>()
    val iconUriMap: LiveData<Map<String, String?>> = _iconUriMap

    private val _mapStates = MutableLiveData<Map<String, ServerMapState>>()
    val mapStates: LiveData<Map<String, ServerMapState>> = _mapStates

    private val _isAudioStreaming = MutableLiveData<Boolean>()
    val isAudioStreaming: LiveData<Boolean> = _isAudioStreaming

    private val pointHistories = mutableMapOf<String, MutableList<Point>>()
    private val hasCentered = mutableSetOf<String>()
    private val lineColors = mutableMapOf<String, Int>()
    private var nextColorHue = 0f

    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected

    private val _toastMessage = MutableLiveData<Event<String>>()
    val toastMessage: LiveData<Event<String>> = _toastMessage

    private val serverLocationMap = mutableMapOf<String, LocationData>()

    var lastKnownCenter: Point? = null

    init {
        observeConnection()
        // 🔧 Временный фейковый сервер для отладки MapFragment
        val testPoint1 = Point(55.751244, 37.618423) // Москва
        val testPoint2 = Point(55.752, 37.619)
        val testState = ServerMapState(
            latestPoint = testPoint1,
            history = listOf(testPoint1, testPoint2),
            shouldCenter = true,
            color = Color.RED,
            timestamp = System.currentTimeMillis()
        )

        _mapStates.postValue(mapOf("testServer" to testState))
        _serverNameMap.postValue(mapOf("testServer" to "Тестовый сервер"))
        _iconUriMap.postValue(mapOf("testServer" to null))
        _isConnected.postValue(true)
    }

    private fun observeConnection() {
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        ref.child(".info/connected").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                _isConnected.postValue(connected)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                _isConnected.postValue(false)
            }
        })
    }

    fun loadAndObserveServers(forceUpdate: Boolean = false) {
        Log.d("DELETE_BTN", "loadAndObserveServers called, forceUpdate=$forceUpdate")

        viewModelScope.launch {
            val servers = db.serverDao().getAll()
            Log.d("DELETE_BTN", "db.serverDao().getAll(): $servers")

            val nameMap = servers.associate { it.serverId to it.name }
            val iconMap = servers.associate { it.serverId to it.serverIconUri }

            _serverNameMap.postValue(nameMap)
            _iconUriMap.postValue(iconMap)

            val serverIds = servers.map { it.serverId }

            if (!observingStarted || forceUpdate) {
                observingStarted = true
                observeAllServerLocations(serverIds)
            }
        }
    }

    fun addServer(serverId: String, code: String, name: String, iconUri: String? = null) {
        viewModelScope.launch {
            // 1. Сохраняем в БД
            val entity = ServerEntity(id = 0, serverId = serverId, code = code, name = name, serverIconUri = iconUri)
            db.serverDao().insert(entity)
            Log.d("CLIENT_ADD", "💾 Сервер сохранён: $serverId, name=$name")

            // 2. Обновляем карты в памяти
            val updatedNames = _serverNameMap.value.orEmpty().toMutableMap()
            updatedNames[serverId] = name
            _serverNameMap.postValue(updatedNames)

            val updatedIcons = _iconUriMap.value.orEmpty().toMutableMap()
            updatedIcons[serverId] = iconUri
            _iconUriMap.postValue(updatedIcons)

            // 3. Запускаем наблюдение за координатами
            Log.d("CLIENT_ADD", "🔄 Подписка на координаты сервера: $serverId")
            observeAllServerLocations(listOf(serverId))
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            Log.d("DELETE_BTN", "before db.serverDao().deleteByServerId(serverId)")
            db.serverDao().deleteByServerId(serverId)
            Log.d("DELETE_BTN", "after db.serverDao().deleteByServerId(serverId)")
            loadAndObserveServers(forceUpdate = true)
        }
    }

    private fun observeAllServerLocations(serverIds: List<String>) {
        Log.d("DELETE_BTN", "observeAllServerLocations")
        Log.d("CLIENT", "🔄 observeAllServerLocations() вызван")
        serverIds.forEach { serverId ->
            repository.observeServerLocation(serverId) { location ->
                serverLocationMap[serverId] = location
                updateLocation(serverId, location)
            }
        }
    }

    private fun updateLocation(serverId: String, location: LocationData) {
        Log.d("CLIENT", "📍 updateLocation() $serverId -> $location")
        val point = Point(location.latitude, location.longitude)

        val history = pointHistories.getOrPut(serverId) { mutableListOf() }
        val last = history.lastOrNull()

        if (last == null || distanceBetween(last, point) > 5) {
            history.add(point)
            if (history.size > 50) history.removeAt(0) // ✅ совместимо с API 21+
        }

        val color = lineColors.getOrPut(serverId) {
            val c = Color.HSVToColor(floatArrayOf(nextColorHue, 1f, 1f))
            nextColorHue = (nextColorHue + 47f) % 360f
            c
        }

        val shouldCenter = hasCentered.add(serverId)

        val state = ServerMapState(
            latestPoint = point,
            history = history.toList(),
            shouldCenter = shouldCenter,
            color = color,
            timestamp = System.currentTimeMillis()
        )

        val current = _mapStates.value.orEmpty().toMutableMap()
        current[serverId] = state
        _mapStates.postValue(current)

        lastKnownCenter = point
    }

    private fun distanceBetween(p1: Point, p2: Point): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(p1.latitude)) *
                cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    fun getIconUriForServer(serverId: String): String? = _iconUriMap.value?.get(serverId)

    fun refreshIcon(serverId: String) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId)
            val current = _iconUriMap.value?.toMutableMap() ?: mutableMapOf()
            current[serverId] = server?.serverIconUri
            _iconUriMap.postValue(current)
        }
    }

    // Шаг 3: Client (ClientViewModel.kt)

    fun sendServerSettings(serverId: String, activeMs: Long, idleMs: Long) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId)
            if (server != null) {
                val code = server.code
                Log.d("CLIENT_CMD", "📤 Отправка интервалов: server=$serverId, code=$code, active=$activeMs, idle=$idleMs")

                val ref = FirebaseDatabase.getInstance()
                    .getReference("server_commands")
                    .child(serverId)
                    .push() // cmd_<auto_id>

                val data = mapOf(
                    "code" to code,
                    "update_settings" to mapOf(
                        "active_interval" to activeMs,
                        "inactivity_timeout" to idleMs
                    )
                )

                ref.setValue(data).addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d("CLIENT_CMD", "✅ Команда отправлена успешно")
                    } else {
                        Log.e("CLIENT_CMD", "❌ Ошибка при отправке команды: ${it.exception}")
                    }
                }
            }
        }
    }


    fun requestServerLocationNow(serverId: String) {
        Log.d("CLIENT_CMD", "Нажатие кнопки запроса координат")
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId)
            if (server != null) {
                val code = server.code
                val ref = FirebaseDatabase.getInstance()
                    .getReference("server_commands/$serverId")
                    .push() // генерация уникального ID команды

                val data = mapOf(
                    "code" to code,
                    "request_location_update" to true
                )

                Log.d("CLIENT_CMD", "📤 Отправка команды запроса координат: $data")
                ref.setValue(data).addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d("CLIENT_CMD", "✅ Команда отправлена: ${ref.key}")
                    } else {
                        Log.e("CLIENT_CMD", "❌ Ошибка при отправке команды: ${it.exception}")
                    }
                }
            } else {
                Log.w("CLIENT_CMD", "⚠️ Сервер не найден в локальной БД: $serverId")
            }
        }
    }


    fun requestListenMocrofoneNow(serverId: String, onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId) ?: return@launch
            val code = server.code
            val commandRef = FirebaseDatabase.getInstance()
                .getReference("server_commands/$serverId")
                .push()
            val command = mapOf(
                "code" to code,
                "type" to "START_AUDIO_STREAM",
                "timestamp" to System.currentTimeMillis()
            )
            commandRef.setValue(command).addOnSuccessListener {
                Log.d("CLIENT_CMD", "✅ Команда на микрофон отправлена: ${commandRef.key}")
                _isAudioStreaming.postValue(true) // ⬅️ уведомление об активации
                onStarted(code)

                viewModelScope.launch {
                    kotlinx.coroutines.delay(30_000)
                    stopAudioStream(serverId, code)
                    _isAudioStreaming.postValue(false) // ⬅️ уведомление об окончании
                }
            }
        }
    }



    fun stopAudioStream(serverId: String, code: String) {
        val ref = FirebaseDatabase.getInstance().getReference("server_commands/$serverId").push()
        val command = mapOf(
            "code" to code,
            "type" to "STOP_AUDIO_STREAM",
            "timestamp" to System.currentTimeMillis()
        )
        ref.setValue(command)
        _isAudioStreaming.postValue(false)
    }




}