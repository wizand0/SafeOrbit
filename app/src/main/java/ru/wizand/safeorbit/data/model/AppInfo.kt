package ru.wizand.safeorbit.data.model

import android.graphics.drawable.Drawable

/**
 * Установленное приложение для экрана выбора источников уведомлений.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    var isAllowed: Boolean
)
