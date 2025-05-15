package ru.wizand.safeorbit.utils

import android.content.Context
import android.location.Location
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.LiveData
import ru.wizand.safeorbit.data.model.LocationData
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Context: быстро показать Toast
fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

// LiveData: безопасно получить значение
fun <T> LiveData<T>.safeValue(): T? = this.value

// Location: преобразование в LocationData
fun Location.toLocationData(): LocationData {
    return LocationData(
        latitude = this.latitude,
        longitude = this.longitude,
        timestamp = System.currentTimeMillis()
    )
}

fun generateReadableId(context: Context): String {
    val date = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
    @Suppress("HardwareIds")
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    val random = (1000..9999).random()

    val base = "$date-$androidId-$random"
    val hash = sha256(base)

    // Возвращаем первые 12 символов хэша в верхнем регистре
    return hash.take(12).uppercase()
}

private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
}
