package ru.wizand.safeorbit.utils

import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.lifecycle.LiveData
import ru.wizand.safeorbit.data.model.LocationData

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
