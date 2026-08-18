package ru.wizand.safeorbit.presentation.role

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager

class RoleSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val encryptedPrefs = EncryptedPreferencesManager.getInstance(application.applicationContext)

    fun saveUserRole(role: UserRole) {
        encryptedPrefs.saveUserRole(role.name)
    }

    fun getUserRole(): UserRole? {
        val value = encryptedPrefs.getUserRole()
        return value?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
    }
}
