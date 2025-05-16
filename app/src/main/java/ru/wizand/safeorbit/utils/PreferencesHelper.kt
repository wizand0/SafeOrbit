package ru.wizand.safeorbit.utils

import android.content.Context

object PreferencesHelper {
    fun saveNavigationPreference(context: Context, value: String) {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .edit().putString("nav_app", value).apply()
    }

    fun getNavigationPreference(context: Context): String =
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("nav_app", "google") ?: "google"
}