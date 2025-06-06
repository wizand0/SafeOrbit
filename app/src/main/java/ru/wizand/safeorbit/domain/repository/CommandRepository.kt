package ru.wizand.safeorbit.domain.repository

interface CommandRepository {
    suspend fun sendUpdateSettingsCommand(
        serverId: String,
        code: String,
        activeMs: Long,
        idleMs: Long
    ): Result<Unit>

    suspend fun sendLocationUpdateCommand(serverId: String, code: String): Result<Unit>

    suspend fun sendStartAudioCommand(serverId: String, code: String): Result<Unit>

    suspend fun sendStopAudioCommand(serverId: String, code: String): Result<Unit>
}
