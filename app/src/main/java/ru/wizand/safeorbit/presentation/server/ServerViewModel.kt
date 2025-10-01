package ru.wizand.safeorbit.presentation.server

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.AudioRequest
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager

/**
 * ViewModel для управления состоянием сервера.
 * Использует EncryptedPreferencesManager для безопасного хранения данных.
 */
class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository(application.applicationContext)
    private val encryptedPrefs = EncryptedPreferencesManager(application.applicationContext)

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

    /**
     * Проверяет наличие сохранённых данных сервера или регистрирует новый
     */
    private fun checkOrRegisterServer() {
        val savedId = encryptedPrefs.getServerId()
        val savedCode = encryptedPrefs.getCode()

        if (savedId != null && savedCode != null) {
            Log.d(TAG, "Используем сохранённый serverId и code")
            _serverId.value = savedId
            _code.value = savedCode
            observeAudioRequest(savedId)
        } else {
            Log.d(TAG, "Регистрируем новый сервер...")
            registerServer()
        }
    }

    /**
     * Регистрация нового сервера в Firebase
     * @param forceNew - принудительная регистрация нового сервера
     */
    fun registerServer(forceNew: Boolean = false) {
        if (!forceNew && encryptedPrefs.isServerRegistered()) {
            Log.d(TAG, "Сервер уже зарегистрирован, пропускаем.")
            return
        }

        repository.registerServer { id, generatedCode ->
            // Сохраняем в зашифрованном хранилище
            encryptedPrefs.saveServerId(id)
            encryptedPrefs.saveCode(generatedCode)

            _serverId.postValue(id)
            _code.postValue(generatedCode)
            observeAudioRequest(id)

            Log.i(TAG, "Сервер зарегистрирован с ID: $id")
        }
    }

    /**
     * Сброс всех данных сервера
     */
    fun reset() {
        encryptedPrefs.clearAll()
        _serverId.postValue(null)
        _code.postValue(null)
        Log.i(TAG, "Данные сервера сброшены")
    }

    /**
     * Подписка на запросы аудио от клиентов
     */
    private fun observeAudioRequest(serverId: String) {
        repository.observeAudioRequest(serverId) { request ->
            _audioRequest.postValue(request)
        }
    }

    /**
     * Отправка данных о местоположении в Firebase
     */
    fun sendLocation(location: LocationData) {
        val id = _serverId.value
        if (id != null) {
            repository.sendLocation(id, location)
        } else {
            Log.w(TAG, "serverId не задан — координаты не отправлены")
        }
    }

    /**
     * Обновление последних известных координат
     */
    fun updateLastLocation(lat: Double, lon: Double, timestamp: Long) {
        _lastKnownLatLon.postValue(lat to lon)
        _lastUpdateTimestamp.postValue(timestamp)
    }

    /**
     * Обновление режима работы сервера
     */
    fun updateMode(mode: String) {
        _mode.postValue(mode)
    }

    companion object {
        private const val TAG = "ServerViewModel"
    }
}