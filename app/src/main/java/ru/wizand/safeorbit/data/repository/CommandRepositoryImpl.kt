package ru.wizand.safeorbit.data.repository

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import ru.wizand.safeorbit.domain.repository.CommandRepository
import javax.inject.Inject

class CommandRepositoryImpl @Inject constructor() : CommandRepository {

    private val db = FirebaseDatabase.getInstance()

    override suspend fun sendUpdateSettingsCommand(
        serverId: String,
        code: String,
        activeMs: Long,
        idleMs: Long
    ): Result<Unit> {
        return try {
            val ref = FirebaseDatabase.getInstance()
                .getReference("server_commands/$serverId")
                .push()

            val data = mapOf(
                "code" to code,
                "update_settings" to mapOf(
                    "active_interval" to activeMs,
                    "inactivity_timeout" to idleMs
                )
            )

            ref.setValue(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendLocationUpdateCommand(serverId: String, code: String): Result<Unit> {
        return try {
            val ref = db.getReference("server_commands/$serverId").push()
            val data = mapOf("code" to code, "request_location_update" to true)
            ref.setValue(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendStartAudioCommand(serverId: String, code: String): Result<Unit> {
        return try {
            val ref = db.getReference("server_commands/$serverId").push()
            val command = mapOf(
                "code" to code,
                "type" to "START_AUDIO_STREAM",
                "timestamp" to System.currentTimeMillis()
            )
            ref.setValue(command).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendStopAudioCommand(serverId: String, code: String): Result<Unit> {
        return try {
            val ref = db.getReference("server_commands/$serverId").push()
            val command = mapOf(
                "code" to code,
                "type" to "STOP_AUDIO_STREAM",
                "timestamp" to System.currentTimeMillis()
            )
            ref.setValue(command).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
