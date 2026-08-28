package ru.wizand.safeorbit.presentation.server.worker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager

class IdleLocationWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val encryptedPrefs = EncryptedPreferencesManager.getInstance(applicationContext)
        val serverId = encryptedPrefs.getServerId() ?: return Result.failure()

        val role = encryptedPrefs.getUserRole()
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
            // 2.2 (аудит): в режиме ЭКОНОМ высокоточный GPS-фикс не нужен.
            // BALANCED_POWER_ACCURACY даёт точность ~50 м (сеть/вышки),
            // энергопотребление снижается в разы; для контроля присутствия достаточно.
            // Активный режим по-прежнему использует PRIORITY_HIGH_ACCURACY
            // в LocationService.startLocationUpdates().
            location = locationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                null
            ).await()
        } catch (e: SecurityException) {
            Log.e("LOCATION", "❌ Нет разрешения на получение локации: ${e.message}")
        }

        if (location != null) {
            Log.d("IdleLocationWorker", "📤 Отправка координат")
            // Примечание: перевод на DI-синглтон (п.4.1 плана) отложен —
            // пока создаётся экземпляр напрямую, поведение идентично.
            FirebaseRepository(applicationContext).sendLocation(
                serverId,
                LocationData(location.latitude, location.longitude, System.currentTimeMillis())
            )
            // Подсветка в уведомлении Foreground-сервиса: момент реальной отправки
            // координат в эко-режиме (LocationService ловит этот broadcast).
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                Intent("IDLE_LOCATION_SENT").apply {
                    putExtra("timestamp", System.currentTimeMillis())
                }
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
