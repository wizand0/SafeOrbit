package ru.wizand.safeorbit.presentation.server

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
    private val _connectedClients = MutableLiveData<List<String>>()
    val connectedClients: LiveData<List<String>> = _connectedClients
    private var clientsListener: ValueEventListener? = null
    private var locationListener: ValueEventListener? = null

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

    /**
     * Перевыпуск pairing-токена (кнопка «Новый код» в диалоге QR-кода):
     * обновляет tokenHash/expiresAt/consumed в БД и локальное хранилище.
     */
    fun rotatePairing(onResult: (Boolean) -> Unit) {
        val id = _serverId.value ?: encryptedPrefs.getServerId()
        if (id.isNullOrBlank()) {
            onResult(false)
            return
        }
        repository.rotatePairing(id) { token ->
            if (token != null) {
                encryptedPrefs.savePairingToken(token)
                _pairingToken.postValue(token)
            }
            onResult(token != null)
        }
    }

    fun fetchPairingState(onResult: (FirebaseRepository.PairingState?) -> Unit) {
        val id = _serverId.value ?: encryptedPrefs.getServerId()
        if (id.isNullOrBlank()) {
            onResult(null)
            return
        }
        repository.fetchPairingState(id, onResult)
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

    /**
     * Подписка сервера на собственные координаты в Firebase.
     *
     * UI (ServerMainFragment) раньше обновлялся только через локальный broadcast от
     * LocationService. Координаты, отправленные из IdleLocationWorker или по команде
     * клиента «обновить координаты», в broadcast не попадали, поэтому экран не обновлялся,
     * пока его не переоткроют. Подписка на servers/$serverId/location закрывает все источники.
     */
    fun observeLocation() {
        if (locationListener != null) return
        val serverId = _serverId.value ?: return
        locationListener = repository.observeServerLocation(serverId) { location ->
            _lastKnownLatLon.postValue(location.latitude to location.longitude)
            if (location.timestamp > 0L) {
                _lastUpdateTimestamp.postValue(location.timestamp)
            }
            location.mode?.let { _mode.postValue(it) }
        }
    }

    fun observeConnectedClients() {
        val serverId = _serverId.value ?: return
        val clientsRef = FirebaseDatabase.getInstance().getReference("server_clients").child(serverId)
        
        clientsListener = clientsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val clients = mutableListOf<String>()
                snapshot.children.forEach { child ->
                    child.key?.let { clients.add(it) }
                }
                _connectedClients.postValue(clients)
                Log.d("ServerViewModel", "✅ Подключённые клиенты обновлены: ${clients.size}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ServerViewModel", "❌ Ошибка получения списка клиентов: ${error.message}")
            }
        })
    }

    fun revokeClientAccess(clientUid: String, onComplete: (Boolean) -> Unit) {
        val serverId = _serverId.value
        if (serverId.isNullOrBlank()) {
            Log.e("ServerViewModel", "❌ Невозможно отозвать доступ: serverId не определён")
            onComplete(false)
            return
        }
        repository.revokeAccess(serverId, clientUid, onComplete)
    }

    override fun onCleared() {
        super.onCleared()
        val serverId = _serverId.value
        if (!serverId.isNullOrBlank()) {
            clientsListener?.let {
                FirebaseDatabase.getInstance().getReference("server_clients").child(serverId).removeEventListener(it)
            }
            locationListener?.let {
                repository.stopObservingServerLocation(serverId, it)
            }
        }
        clientsListener = null
        locationListener = null
    }
}
