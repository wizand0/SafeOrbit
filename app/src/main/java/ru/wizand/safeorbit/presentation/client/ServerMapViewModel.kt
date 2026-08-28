package ru.wizand.safeorbit.presentation.client

import android.graphics.Color
import androidx.lifecycle.*
import com.google.firebase.database.ValueEventListener
import com.yandex.mapkit.geometry.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import javax.inject.Inject
import kotlin.math.*

data class ServerMapState(
    val latestPoint: Point,
    val history: List<Point>,
    val shouldCenter: Boolean,
    val color: Int,
    val timestamp: Long
)

@HiltViewModel
class ServerMapViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _mapStates = MutableLiveData<Map<String, ServerMapState>>()
    val mapStates: LiveData<Map<String, ServerMapState>> = _mapStates

    /**
     * Активные Firebase-подписки по serverId. Гарантирует ровно одну подписку
     * на узел координат при любом числе вызовов observeServerLocations
     * (ранее каждый вызов из onResume/эмитов LiveData добавлял новый
     * ValueEventListener, и одна координата обрабатывалась N раз).
     */
    private val locationListeners = mutableMapOf<String, ValueEventListener>()

    private val pointHistories = mutableMapOf<String, MutableList<Point>>()
    private val hasCentered = mutableSetOf<String>()
    private val lineColors = mutableMapOf<String, Int>()
    private var nextColorHue = 0f

    var lastKnownCenter: Point? = null
        private set

    /**
     * Идемпотентная подписка на координаты: для уже подписанных serverId
     * повторная регистрация не выполняется; подписки на удалённые серверы снимаются.
     */
    fun observeServerLocations(serverIds: List<String>) {
        // Снять подписки на серверы, которых больше нет
        val stale = locationListeners.keys - serverIds.toSet()
        stale.forEach { serverId ->
            locationListeners.remove(serverId)?.let { repository.stopObservingServerLocation(serverId, it) }
            pointHistories.remove(serverId)
            hasCentered.remove(serverId)
        }

        serverIds.forEach { serverId ->
            if (locationListeners.containsKey(serverId)) return@forEach
            val listener = repository.observeServerLocation(serverId) { location ->
                updateLocation(serverId, location)
            }
            locationListeners[serverId] = listener
        }
    }

    override fun onCleared() {
        locationListeners.forEach { (serverId, listener) ->
            repository.stopObservingServerLocation(serverId, listener)
        }
        locationListeners.clear()
        super.onCleared()
    }

    private fun updateLocation(serverId: String, location: LocationData) {
        val point = Point(location.latitude, location.longitude)
        val history = pointHistories.getOrPut(serverId) { mutableListOf() }

        if (history.lastOrNull()?.let { distanceBetween(it, point) > 5 } != false) {
            history.add(point)
            if (history.size > 50) history.removeAt(0)
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
            // FIX: время реальной отправки координат сервером, а не момент получения
            // клиентом — иначе на карте «свежее» время, чем в подробных сведениях.
            timestamp = location.timestamp
        )

        val updatedMap = _mapStates.value.orEmpty().toMutableMap()
        updatedMap[serverId] = state
        _mapStates.postValue(updatedMap)

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
}
