package ru.wizand.safeorbit.presentation.server

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager

/**
 * ViewModel ╨┤╨╗╤П ╤Г╨┐╤А╨░╨▓╨╗╨╡╨╜╨╕╤П ╤Б╨╛╤Б╤В╨╛╤П╨╜╨╕╨╡╨╝ ╤Б╨╡╤А╨▓╨╡╤А╨░.
 * ╨Ш╤Б╨┐╨╛╨╗╤М╨╖╤Г╨╡╤В EncryptedPreferencesManager ╨┤╨╗╤П ╨▒╨╡╨╖╨╛╨┐╨░╤Б╨╜╨╛╨│╨╛ ╤Е╤А╨░╨╜╨╡╨╜╨╕╤П ╨┤╨░╨╜╨╜╤Л╤Е.
 */
class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository(application.applicationContext)
    private val encryptedPrefs = EncryptedPreferencesManager(application.applicationContext)

    private val _serverId = MutableLiveData<String?>()
    val serverId: LiveData<String?> = _serverId

    private val _code = MutableLiveData<String?>()
    val code: LiveData<String?> = _code


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
     * ╨Я╤А╨╛╨▓╨╡╤А╤П╨╡╤В ╨╜╨░╨╗╨╕╤З╨╕╨╡ ╤Б╨╛╤Е╤А╨░╨╜╤С╨╜╨╜╤Л╤Е ╨┤╨░╨╜╨╜╤Л╤Е ╤Б╨╡╤А╨▓╨╡╤А╨░ ╨╕╨╗╨╕ ╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╤Г╨╡╤В ╨╜╨╛╨▓╤Л╨╣
     */
    private fun checkOrRegisterServer() {
        val savedId = encryptedPrefs.getServerId()
        val savedCode = encryptedPrefs.getCode()

        if (savedId != null && savedCode != null) {
            Log.d(TAG, "╨Ш╤Б╨┐╨╛╨╗╤М╨╖╤Г╨╡╨╝ ╤Б╨╛╤Е╤А╨░╨╜╤С╨╜╨╜╤Л╨╣ serverId ╨╕ code")
            _serverId.value = savedId
            _code.value = savedCode
        } else {
            Log.d(TAG, "╨а╨╡╨│╨╕╤Б╤В╤А╨╕╤А╤Г╨╡╨╝ ╨╜╨╛╨▓╤Л╨╣ ╤Б╨╡╤А╨▓╨╡╤А...")
            registerServer()
        }
    }

    /**
     * ╨а╨╡╨│╨╕╤Б╤В╤А╨░╤Ж╨╕╤П ╨╜╨╛╨▓╨╛╨│╨╛ ╤Б╨╡╤А╨▓╨╡╤А╨░ ╨▓ Firebase
     * @param forceNew - ╨┐╤А╨╕╨╜╤Г╨┤╨╕╤В╨╡╨╗╤М╨╜╨░╤П ╤А╨╡╨│╨╕╤Б╤В╤А╨░╤Ж╨╕╤П ╨╜╨╛╨▓╨╛╨│╨╛ ╤Б╨╡╤А╨▓╨╡╤А╨░
     */
    fun registerServer(forceNew: Boolean = false) {
        if (!forceNew && encryptedPrefs.isServerRegistered()) {
            Log.d(TAG, "╨б╨╡╤А╨▓╨╡╤А ╤Г╨╢╨╡ ╨╖╨░╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╨╛╨▓╨░╨╜, ╨┐╤А╨╛╨┐╤Г╤Б╨║╨░╨╡╨╝.")
            return
        }

        repository.registerServer { id, generatedCode ->
            // ╨б╨╛╤Е╤А╨░╨╜╤П╨╡╨╝ ╨▓ ╨╖╨░╤И╨╕╤Д╤А╨╛╨▓╨░╨╜╨╜╨╛╨╝ ╤Е╤А╨░╨╜╨╕╨╗╨╕╤Й╨╡
            encryptedPrefs.saveServerId(id)
            encryptedPrefs.saveCode(generatedCode)

            _serverId.postValue(id)
            _code.postValue(generatedCode)

            Log.i(TAG, "╨б╨╡╤А╨▓╨╡╤А ╨╖╨░╤А╨╡╨│╨╕╤Б╤В╤А╨╕╤А╨╛╨▓╨░╨╜ ╤Б ID: $id")
        }
    }

    /**
     * ╨б╨▒╤А╨╛╤Б ╨▓╤Б╨╡╤Е ╨┤╨░╨╜╨╜╤Л╤Е ╤Б╨╡╤А╨▓╨╡╤А╨░
     */
    fun reset() {
        encryptedPrefs.clearAll()
        _serverId.postValue(null)
        _code.postValue(null)
        Log.i(TAG, "╨Ф╨░╨╜╨╜╤Л╨╡ ╤Б╨╡╤А╨▓╨╡╤А╨░ ╤Б╨▒╤А╨╛╤И╨╡╨╜╤Л")
    }

    /**
     * ╨Ю╤В╨┐╤А╨░╨▓╨║╨░ ╨┤╨░╨╜╨╜╤Л╤Е ╨╛ ╨╝╨╡╤Б╤В╨╛╨┐╨╛╨╗╨╛╨╢╨╡╨╜╨╕╨╕ ╨▓ Firebase
     */
    fun sendLocation(location: LocationData) {
        val id = _serverId.value
        if (id != null) {
            repository.sendLocation(id, location)
        } else {
            Log.w(TAG, "serverId ╨╜╨╡ ╨╖╨░╨┤╨░╨╜ тАФ ╨║╨╛╨╛╤А╨┤╨╕╨╜╨░╤В╤Л ╨╜╨╡ ╨╛╤В╨┐╤А╨░╨▓╨╗╨╡╨╜╤Л")
        }
    }

    /**
     * ╨Ю╨▒╨╜╨╛╨▓╨╗╨╡╨╜╨╕╨╡ ╨┐╨╛╤Б╨╗╨╡╨┤╨╜╨╕╤Е ╨╕╨╖╨▓╨╡╤Б╤В╨╜╤Л╤Е ╨║╨╛╨╛╤А╨┤╨╕╨╜╨░╤В
     */
    fun updateLastLocation(lat: Double, lon: Double, timestamp: Long) {
        _lastKnownLatLon.postValue(lat to lon)
        _lastUpdateTimestamp.postValue(timestamp)
    }

    /**
     * ╨Ю╨▒╨╜╨╛╨▓╨╗╨╡╨╜╨╕╨╡ ╤А╨╡╨╢╨╕╨╝╨░ ╤А╨░╨▒╨╛╤В╤Л ╤Б╨╡╤А╨▓╨╡╤А╨░
     */
    fun updateMode(mode: String) {
        _mode.postValue(mode)
    }

    companion object {
        private const val TAG = "ServerViewModel"
    }
}
