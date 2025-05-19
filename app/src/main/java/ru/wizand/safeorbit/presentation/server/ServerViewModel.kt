package ru.wizand.safeorbit.presentation.server

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.AudioRequest
import ru.wizand.safeorbit.data.model.LocationData

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)

    private val _serverId = MutableLiveData<String?>()
    val serverId: LiveData<String?> = _serverId

    private val _code = MutableLiveData<String?>()
    val code: LiveData<String?> = _code

    private val _audioRequest = MutableLiveData<AudioRequest>()
    val audioRequest: LiveData<AudioRequest> = _audioRequest

    // ➕ Добавлены поля для последних координат, времени обновления и режима
    private val _lastKnownLatLon = MutableLiveData<Pair<Double, Double>>()
    val lastKnownLatLon: LiveData<Pair<Double, Double>> = _lastKnownLatLon

    private val _lastUpdateTimestamp = MutableLiveData<Long>()
    val lastUpdateTimestamp: LiveData<Long> = _lastUpdateTimestamp

    private val _mode = MutableLiveData<String>()
    val mode: LiveData<String> = _mode

    init {
        val savedId = prefs.getString("server_id", null)
        val savedCode = prefs.getString("server_code", null)

        if (savedId != null && savedCode != null) {
            val id = savedId
            val code = savedCode

            _serverId.value = id
            _code.value = code
            observeAudioRequest(id)
        } else {
            registerServer()
        }
    }

    fun registerServer() {
        repository.registerServer { id, generatedCode ->
            _serverId.postValue(id)
            _code.postValue(generatedCode)

            prefs.edit()
                .putString("server_id", id)
                .putString("server_code", generatedCode)
                .apply()

            observeAudioRequest(id)
        }
    }

    private fun observeAudioRequest(serverId: String) {
        repository.observeAudioRequest(serverId) {
            _audioRequest.postValue(it)
        }
    }

    fun sendLocation(location: LocationData) {
        val serverIdValue = _serverId.value
        if (serverIdValue != null) {
            Log.d("SEND_LOCATION", "Отправляем координаты на сервер: $location")
            repository.sendLocation(serverIdValue, location)
        } else {
            Log.w("SEND_LOCATION", "serverId еще не установлен — координаты не отправлены")
        }
    }

    fun updateLastLocation(lat: Double, lon: Double, timestamp: Long) {
        _lastKnownLatLon.postValue(lat to lon)
        _lastUpdateTimestamp.postValue(timestamp)
    }

    fun updateMode(mode: String) {
        _mode.postValue(mode)
    }

    fun reset() {
        prefs.edit().clear().apply()
        _serverId.postValue(null)
        _code.postValue(null)
    }
}
