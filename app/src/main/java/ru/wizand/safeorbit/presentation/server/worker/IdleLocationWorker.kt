package ru.wizand.safeorbit.presentation.server.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class IdleLocationWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serverId = prefs.getString("server_id", null) ?: return Result.failure()

        val role = prefs.getString("user_role", null)
        if (role != UserRole.SERVER.name) {
            Log.w("IdleLocationWorker", "⛔ Не режим сервера, отмена")
            return Result.failure()
        }

        if (!hasPermission()) {
            Log.w("IdleLocationWorker", "❌ Нет разрешений")
            return Result.failure()
        }

        val locationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        var location: Location? = null

        try {
            location = locationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
            // Использовать location
        } catch (e: SecurityException) {
            Log.e("LOCATION", "❌ Нет разрешения на получение локации: ${e.message}")
        }

        if (location != null) {
            Log.d("IdleLocationWorker", "📤 Отправка координат: ${location.latitude}, ${location.longitude}")
            FirebaseRepository(applicationContext).sendLocation(
                serverId,
                LocationData(location.latitude, location.longitude, System.currentTimeMillis())
            )
            return Result.success()
        }

        Log.w("IdleLocationWorker", "⚠️ Локация не получена")
        return Result.retry()
    }

    private fun hasPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED
    }
}
