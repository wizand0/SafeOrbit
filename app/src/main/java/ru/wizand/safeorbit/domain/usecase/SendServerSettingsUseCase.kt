package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.domain.repository.CommandRepository
import javax.inject.Inject

class SendServerSettingsUseCase @Inject constructor(
    private val db: AppDatabase,
    private val commandRepository: CommandRepository
) {
    suspend operator fun invoke(
        serverId: String,
        activeMs: Long,
        idleMs: Long
    ): Result<Unit> {
        val server = db.serverDao().getByServerId(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))

        return commandRepository.sendUpdateSettingsCommand(serverId, server.code, activeMs, idleMs)
    }
}
