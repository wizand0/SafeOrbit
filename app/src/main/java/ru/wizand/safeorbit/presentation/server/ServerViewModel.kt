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
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _serverId = MutableLiveData<String?>()
    val serverId: LiveData<String?> = _serverId

    private val _code = MutableLiveData<String?>()
    val code: LiveData<String?> = _code

    private val _audioRequest = MutableLiveData<AudioRequest>()
    val audioRequest: LiveData<AudioRequest> = _audioRequest

    private val _lastKnownLatLon = MutableLiveData<Pair<Double, Double>>()
    val lastKnownLatLon: LiveData<Pair<Double, Double>> = _lastKnownLatLon

    private val _lastUpdateTimestamp = MutableLiveData<Long>()
    val lastUpdateTimestamp: LiveData<Long> = _lastUpdateTimestamp

    private val _mode = MutableLiveData<String>()
    val mode: LiveData<String> = _mode

    init {
        checkOrRegisterServer()
    }

    private fun checkOrRegisterServer() {
        val savedId = prefs.getString("server_id", null)
        val savedCode = prefs.getString("server_code", null)

        if (savedId != null && savedCode != null) {
            Log.d("ServerViewModel", "Используем сохранённый serverId и code")
            _serverId.value = savedId
            _code.value = savedCode
            observeAudioRequest(savedId)
        } else {
            Log.d("ServerViewModel", "Регистрируем новый сервер...")
            registerServer()
        }
    }

    fun registerServer(forceNew: Boolean = false) {
        if (!forceNew && _serverId.value != null && _code.value != null) {
            Log.d("ServerViewModel", "Сервер уже зарегистрирован, пропускаем.")
            return
        }

        repository.registerServer { id, generatedCode ->
            prefs.edit()
                .putString("server_id", id)
                .putString("server_code", generatedCode)
                .apply()

            _serverId.postValue(id)
            _code.postValue(generatedCode)
            observeAudioRequest(id)
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
        _serverId.postValue(null)
        _code.postValue(null)
    }

    private fun observeAudioRequest(serverId: String) {
        repository.observeAudioRequest(serverId) {
            _audioRequest.postValue(it)
        }
    }

    fun sendLocation(location: LocationData) {
        val id = _serverId.value
        if (id != null) {
            repository.sendLocation(id, location)
        } else {
            Log.w("ServerViewModel", "serverId не задан — координаты не отправлены")
        }
    }

    fun updateLastLocation(lat: Double, lon: Double, timestamp: Long) {
        _lastKnownLatLon.postValue(lat to lon)
        _lastUpdateTimestamp.postValue(timestamp)
    }

    fun updateMode(mode: String) {
        _mode.postValue(mode)
    }
}