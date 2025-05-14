package ru.wizand.safeorbit.presentation.client

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData

class ClientViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseRepository()

    private val _pairingResult = MutableLiveData<Boolean>()
    val pairingResult: LiveData<Boolean> = _pairingResult

    private val _serverLocation = MutableLiveData<LocationData>()
    val serverLocation: LiveData<LocationData> = _serverLocation

    private var currentServerId: String? = null

    fun pairWithServer(serverId: String, code: String) {
        repository.pairClientToServer(serverId, code) { success ->
            if (success) {
                currentServerId = serverId
                observeLocation(serverId)
            }
            _pairingResult.postValue(success)
        }
    }

    private fun observeLocation(serverId: String) {
        repository.observeServerLocation(serverId) {
            _serverLocation.postValue(it)
        }
    }

    fun sendAudioRequest() {
        currentServerId?.let { repository.sendAudioRequest(it) }
    }
}