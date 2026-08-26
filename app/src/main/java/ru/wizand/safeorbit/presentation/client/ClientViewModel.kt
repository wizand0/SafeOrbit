package ru.wizand.safeorbit.presentation.client

import androidx.lifecycle.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

    // 1.5: держим ссылку на конкретный узел БД, к которому подписывались,
    // чтобы снять подписку с ТОГО ЖЕ узла и ТЕМ ЖЕ объектом-листенером.
    private val connectionRef = FirebaseDatabase.getInstance().reference.child(".info/connected")

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            _isConnected.postValue(snapshot.getValue(Boolean::class.java) ?: false)
        }

        override fun onCancelled(error: DatabaseError) {
            _isConnected.postValue(false)
        }
    }

    init {
        observeConnection()
    }

    private fun observeConnection() {
        connectionRef.addValueEventListener(connectionListener)
    }

    fun loadAndObserveServers() {
        viewModelScope.launch {
            val servers = db.serverDao().getAll()
            _serverNameMap.postValue(servers.associate { it.serverId to it.name })
            _iconUriMap.postValue(servers.associate { it.serverId to it.serverIconUri })
        }
    }

    fun addServer(serverId: String, pairingToken: String, name: String, iconUri: String? = null) {
        viewModelScope.launch {
            val server = Server(serverId, pairingToken, name, iconUri)  // используем pairingToken вместо кода
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

    /**
     * Переименование сервера на стороне клиента.
     * Пишет ТОЛЬКО в локальную Room БД клиента (поле displayName),
     * не трогая узел servers/{serverId} в Firebase — там у клиента
     * по текущим Firebase Rules нет прав на запись, а сам сервер
     * этот rename не должен видеть/чувствовать (см. п.4 плана).
     */
    fun renameServer(serverId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            db.serverDao().updateName(serverId, trimmed)
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

    // 1.5: снимаем подписку тем же объектом-листенером с того же узла при уничтожении VM
    override fun onCleared() {
        super.onCleared()
        connectionRef.removeEventListener(connectionListener)
    }
}