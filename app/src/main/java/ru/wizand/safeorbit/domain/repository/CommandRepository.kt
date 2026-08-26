package ru.wizand.safeorbit.domain.repository

interface CommandRepository {
    suspend fun sendUpdateSettingsCommand(
        serverId: String,
        activeMs: Long,
        idleMs: Long
    ): Result<Unit>

    suspend fun sendLocationUpdateCommand(serverId: String): Result<Unit>
}
