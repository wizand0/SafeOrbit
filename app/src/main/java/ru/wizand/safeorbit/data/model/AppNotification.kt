package ru.wizand.safeorbit.data.model

/**
 * Уведомление, перехваченное сервером и доставленное клиенту.
 */
data class AppNotification(
    val firebaseKey: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long
)
