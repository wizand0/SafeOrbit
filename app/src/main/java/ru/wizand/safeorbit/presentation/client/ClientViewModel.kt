package ru.wizand.safeorbit.presentation.client

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.ServerEntity
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
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

    private val _serverNameMap = MutableLiveData<Map<String, String>>()
    val serverNameMap: LiveData<Map<String, String>> = _serverNameMap

    private val _iconUriMap = MutableLiveData<Map<String, String?>>()
    val iconUriMap: LiveData<Map<String, String?>> = _iconUriMap

    private val _mapStates = MutableLiveData<Map<String, ServerMapState>>()
    val mapStates: LiveData<Map<String, ServerMapState>> = _mapStates

    private val pointHistories = mutableMapOf<String, MutableList<Point>>()
    private val hasCentered = mutableSetOf<String>()
    private val lineColors = mutableMapOf<String, Int>()
    private var nextColorHue = 0f

    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected

    private val serverLocationMap = mutableMapOf<String, LocationData>()

    var lastKnownCenter: Point? = null

    init {
        observeConnection()
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

    fun loadAndObserveServers() {
        viewModelScope.launch {
            val servers = db.serverDao().getAll()

            val nameMap = servers.associate { it.serverId to it.name }
            val iconMap = servers.associate { it.serverId to it.serverIconUri }

            _serverNameMap.postValue(nameMap)
            _iconUriMap.postValue(iconMap)

            val serverIds = servers.map { it.serverId }
            observeAllServerLocations(serverIds)
        }
    }

    fun addServer(serverId: String, code: String, name: String) {
        viewModelScope.launch {
            db.serverDao().insert(
                ServerEntity(id = 0, serverId = serverId, code = code, name = name)
            )
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            db.serverDao().deleteByServerId(serverId)
            loadAndObserveServers()
        }
    }

    private fun observeAllServerLocations(serverIds: List<String>) {
        serverIds.forEach { serverId ->
            repository.observeServerLocation(serverId) { location ->
                serverLocationMap[serverId] = location
                updateLocation(serverId, location)
            }
        }
    }

    private fun updateLocation(serverId: String, location: LocationData) {
        val point = Point(location.latitude, location.longitude)

        val history = pointHistories.getOrPut(serverId) { mutableListOf() }
        val last = history.lastOrNull()

        if (last == null || distanceBetween(last, point) > 5) {
            history.add(point)
            if (history.size > 50) history.removeFirst()
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
}
