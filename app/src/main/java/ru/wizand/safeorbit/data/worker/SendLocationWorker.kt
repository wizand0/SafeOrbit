package ru.wizand.safeorbit.data.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData
import java.util.concurrent.TimeUnit

class SendLocationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val serverId = inputData.getString("server_id") ?: return Result.failure()
        val lat = inputData.getDouble("latitude", 0.0)
        val lon = inputData.getDouble("longitude", 0.0)
        val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())

        val repository = FirebaseRepository(applicationContext)
        val data = LocationData(latitude = lat, longitude = lon, timestamp = timestamp)

        return try {
            repository.sendLocation(serverId, data)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry() // попробует снова позже
        }
    }
}
