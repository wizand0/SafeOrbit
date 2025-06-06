package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.domain.repository.CommandRepository
import javax.inject.Inject

class StopAudioStreamUseCase @Inject constructor(
    private val db: AppDatabase,
    private val repo: CommandRepository
) {
    suspend operator fun invoke(serverId: String, code: String): Result<Unit> {
        return repo.sendStopAudioCommand(serverId, code)
    }
}
