package ru.wizand.safeorbit.utils

object Constants {
    const val FIREBASE_SERVERS = "servers"
    const val FIREBASE_CLIENTS = "clients"
    const val PREFS_NAME = "app_prefs"
    const val PREF_USER_ROLE = "user_role"
    const val FIREBASE_DB_URL = ""

    const val LOCATION_UPDATE_INTERVAL_MOVING = 30_000L      // 30 сек
    const val LOCATION_UPDATE_INTERVAL_IDLE = 10 * 60_000L   // 10 мин

    const val NOTIFICATION_CHANNEL_ID = "location_channel"
}
