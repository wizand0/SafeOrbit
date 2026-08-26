package ru.wizand.safeorbit.presentation.client

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.model.AppNotification
import ru.wizand.safeorbit.domain.repository.NotificationRepository
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val db: AppDatabase
) : ViewModel() {

    private val target = MutableStateFlow<Pair<String, String>?>(null)

    /** serverId для активных операций (экспорт, очистка). */
    val activeServerId: String?
        get() = target.value?.first

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<AppNotification>> = target
        .flatMapLatest { pair ->
            if (pair == null) flowOf(emptyList())
            else repository.observe(pair.first, pair.second)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun attach(serverId: String) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId)
            if (server == null) {
                Log.w("NotifVM", "Сервер $serverId не найден в локальной БД")
                return@launch
            }
            target.value = serverId to server.pairingToken  // используем pairingToken вместо кода
        }
    }

    /** Очистка всех уведомлений сервера. Возвращает результат операции. */
    suspend fun clearAll(): Result<Unit> {
        val pair = target.value ?: return Result.failure(IllegalStateException("Сервер не подключён"))
        return repository.clear(pair.first, pair.second)
    }

    /** Pairing токен активного сервера (для экспорта в CSV). */
    suspend fun getServerCode(): String? {  // название оставлено для совместимости, хотя это pairingToken
        val serverId = activeServerId ?: return null
        return db.serverDao().getByServerId(serverId)?.pairingToken  // используем pairingToken вместо кода
    }
}
