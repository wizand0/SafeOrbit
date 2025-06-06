package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.domain.repository.CommandRepository
import javax.inject.Inject

class StartAudioStreamUseCase @Inject constructor(
    private val db: AppDatabase,
    private val repo: CommandRepository
) {
    suspend operator fun invoke(
        serverId: String,
        onStarted: (String) -> Unit,
        onCompleted: () -> Unit
    ): Result<Unit> {
        val server = db.serverDao().getByServerId(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))

        val code = server.code
        val result = repo.sendStartAudioCommand(serverId, code)

        if (result.isSuccess) {
            onStarted(code)
            kotlinx.coroutines.delay(30_000)
            repo.sendStopAudioCommand(serverId, code)
            onCompleted()
        }

        return result
    }
}
