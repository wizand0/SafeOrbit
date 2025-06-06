package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.domain.repository.CommandRepository
import javax.inject.Inject

class RequestServerLocationUseCase @Inject constructor(
    private val db: AppDatabase,
    private val repo: CommandRepository
) {
    suspend operator fun invoke(serverId: String): Result<Unit> {
        val server = db.serverDao().getByServerId(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        return repo.sendLocationUpdateCommand(serverId, server.code)
    }
}