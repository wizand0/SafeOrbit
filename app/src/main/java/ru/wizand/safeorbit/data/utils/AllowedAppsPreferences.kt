package ru.wizand.safeorbit.data.utils

import android.content.Context

/**
 * Хранилище списка пакетов, уведомления которых разрешено перехватывать.
 */
object AllowedAppsPreferences {
    private const val KEY_ALLOWED_PACKAGES = "***"

    fun saveAllowedPackages(context: Context, allowedPackages: Set<String>) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ALLOWED_PACKAGES, allowedPackages).apply()
    }

    fun getAllowedPackages(context: Context): Set<String> {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getStringSet(KEY_ALLOWED_PACKAGES, emptySet()) ?: emptySet()
    }
}
