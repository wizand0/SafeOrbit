package ru.wizand.safeorbit.presentation.server

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.AudioRequest
import ru.wizand.safeorbit.data.model.LocationData

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository(application.applicationContext)

    private val _serverId = MutableLiveData<String>()
    val serverId: LiveData<String> = _serverId

    private val _code = MutableLiveData<String>()
    val code: LiveData<String> = _code

    private val _audioRequest = MutableLiveData<AudioRequest>()
    val audioRequest: LiveData<AudioRequest> = _audioRequest

    fun registerServer() {
        repository.registerServer { id, generatedCode ->
            _serverId.postValue(id)
            _code.postValue(generatedCode)
            observeAudioRequest(id)
        }
    }

    private fun observeAudioRequest(serverId: String) {
        repository.observeAudioRequest(serverId) {
            _audioRequest.postValue(it)
        }
    }

    //    fun sendLocation(location: LocationData) {
//        serverId.value?.let { repository.sendLocation(it, location) }
//    }
    fun sendLocation(location: LocationData) {
        val serverIdValue = _serverId.value
        if (serverIdValue != null) {
            Log.d("SEND_LOCATION", "Отправляем координаты на сервер: $location")
            repository.sendLocation(serverIdValue, location)
        } else {
            Log.w("SEND_LOCATION", "serverId еще не установлен — координаты не отправлены")
        }
    }
}