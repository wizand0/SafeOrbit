package ru.wizand.safeorbit.utils

import android.content.Context
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

object PreferencesHelper {
    fun saveNavigationPreference(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("nav_app", value).apply()
    }

    fun getNavigationPreference(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("nav_app", "google") ?: "google"
}