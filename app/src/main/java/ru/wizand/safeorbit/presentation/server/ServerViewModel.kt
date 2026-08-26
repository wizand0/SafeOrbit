package ru.wizand.safeorbit.presentation.server

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application.applicationContext)
    private val encryptedPrefs = EncryptedPreferencesManager.getInstance(application.applicationContext)
    private val _serverId = MutableLiveData<String?>()
    val serverId: LiveData<String?> = _serverId
    private val _code = MutableLiveData<String?>()
    val code: LiveData<String?> = _code
    private val _pairingToken = MutableLiveData<String?>()
    val pairingToken: LiveData<String?> = _pairingToken
    private val _lastKnownLatLon = MutableLiveData<Pair<Double, Double>>()
    val lastKnownLatLon: LiveData<Pair<Double, Double>> = _lastKnownLatLon
    private val _lastUpdateTimestamp = MutableLiveData<Long>()
    val lastUpdateTimestamp: LiveData<Long> = _lastUpdateTimestamp
    private val _mode = MutableLiveData<String>()
    val mode: LiveData<String> = _mode

    init { checkOrRegisterServer() }

    private fun checkOrRegisterServer() {
        val id = encryptedPrefs.getServerId()
        val code = encryptedPrefs.getCode()
        val token = encryptedPrefs.getPairingToken()
        if (!id.isNullOrBlank() && (!code.isNullOrBlank() || !token.isNullOrBlank())) {
            _serverId.value = id
            _code.value = code
            _pairingToken.value = token
            android.util.Log.d("ServerViewModel", "✅ Сервер уже зарегистрирован: $id")
        } else {
            android.util.Log.d("ServerViewModel", "🔄 Регистрация нового сервера...")
            registerServer()
        }
    }

    fun registerServer(forceNew: Boolean = false) {
        if (!forceNew && encryptedPrefs.isServerRegistered()) return
        repository.registerServer { id, pairingToken ->
            encryptedPrefs.saveServerId(id)
            encryptedPrefs.savePairingToken(pairingToken)
            _serverId.postValue(id)
            _pairingToken.postValue(pairingToken)
            Log.i("ServerViewModel", "Server registered with pairing token: $id")
        }
    }

    fun reset() {
        encryptedPrefs.clearAll()
        _serverId.postValue(null)
        _code.postValue(null)
    }

    fun sendLocation(location: LocationData) {
        _serverId.value?.let { repository.sendLocation(it, location) }
    }

    fun updateLastLocation(lat: Double, lon: Double, timestamp: Long) {
        _lastKnownLatLon.postValue(lat to lon)
        _lastUpdateTimestamp.postValue(timestamp)
    }

    fun updateMode(mode: String) { _mode.postValue(mode) }
}
