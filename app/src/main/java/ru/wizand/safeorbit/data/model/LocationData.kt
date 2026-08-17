package ru.wizand.safeorbit.data.model

import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties

/**
 * Модель данных о местоположении для Firebase Realtime Database.
 *
 * @Keep - предотвращает обфускацию ProGuard
 * @IgnoreExtraProperties - игнорирует неизвестные поля из Firebase
 */
@Keep
@IgnoreExtraProperties
data class LocationData(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var timestamp: Long = 0L,
    var mode: String? = null
) {
    /**
     * Пустой конструктор необходим для Firebase десериализации
     */
    constructor() : this(0.0, 0.0, 0L, null)
}