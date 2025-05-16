package ru.wizand.safeorbit.presentation.client

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase

class ChangeIconViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    fun saveIcon(serverId: String, localPath: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId)
            server?.let {
                db.serverDao().insert(it.copy(serverIconUri = localPath))
                onComplete()
            }
        }
    }
}