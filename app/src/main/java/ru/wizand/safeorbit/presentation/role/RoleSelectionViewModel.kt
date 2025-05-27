package ru.wizand.safeorbit.presentation.role

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class RoleSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserRole(role: UserRole) {
        prefs.edit().putString("user_role", role.name).apply()
    }

    fun getUserRole(): UserRole? {
        val value = prefs.getString("user_role", null)
        return value?.let { UserRole.valueOf(it) }
    }
}




