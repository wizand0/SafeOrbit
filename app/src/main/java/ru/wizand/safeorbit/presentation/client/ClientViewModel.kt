package ru.wizand.safeorbit.presentation.client

import androidx.lifecycle.*
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.domain.model.Server
import ru.wizand.safeorbit.domain.usecase.AddServerUseCase
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val addServerUseCase: AddServerUseCase,
    private val db: AppDatabase
) : ViewModel() {

    private val _serverNameMap = MutableLiveData<Map<String, String>>()
    val serverNameMap: LiveData<Map<String, String>> = _serverNameMap

    private val _iconUriMap = MutableLiveData<Map<String, String?>>()
    val iconUriMap: LiveData<Map<String, String?>> = _iconUriMap

    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected

    init {
        observeConnection()
    }

    private fun observeConnection() {
        FirebaseDatabase.getInstance().reference
            .child(".info/connected")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    _isConnected.postValue(snapshot.getValue(Boolean::class.java) ?: false)
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    _isConnected.postValue(false)
                }
            })
    }

    fun loadAndObserveServers() {
        viewModelScope.launch {
            val servers = db.serverDao().getAll()
            _serverNameMap.postValue(servers.associate { it.serverId to it.name })
            _iconUriMap.postValue(servers.associate { it.serverId to it.serverIconUri })
        }
    }

    fun addServer(serverId: String, code: String, name: String, iconUri: String? = null) {
        viewModelScope.launch {
            val server = Server(serverId, code, name, iconUri)
            addServerUseCase(server)
            loadAndObserveServers()
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            db.serverDao().deleteByServerId(serverId)
            loadAndObserveServers()
        }
    }

    fun refreshIcon(serverId: String) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId)
            val current = _iconUriMap.value?.toMutableMap() ?: mutableMapOf()
            current[serverId] = server?.serverIconUri
            _iconUriMap.postValue(current)
        }
    }

    fun getIconUriForServer(serverId: String): String? =
        _iconUriMap.value?.get(serverId)
}
