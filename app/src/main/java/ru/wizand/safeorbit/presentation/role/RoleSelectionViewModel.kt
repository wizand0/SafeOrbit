package ru.wizand.safeorbit.presentation.role

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.WorkManager
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager

class RoleSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val encryptedPrefs = EncryptedPreferencesManager.getInstance(application.applicationContext)

    fun saveUserRole(role: UserRole) {
        encryptedPrefs.saveUserRole(role.name)
        if (role != UserRole.SERVER) {
            // Старый серверный воркер отменяется, чтобы он не запускался на устройстве-
            // клиенте и не падал мгновенно с Result.failure() («не режим сервера»).
            WorkManager.getInstance(getApplication()).cancelUniqueWork("idle_location_fetch")
        }
    }

    fun getUserRole(): UserRole? {
        val value = encryptedPrefs.getUserRole()
        return value?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
    }
}
