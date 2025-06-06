package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.domain.model.Server
import ru.wizand.safeorbit.domain.repository.ServerRepository

class AddServerUseCase(private val repository: ServerRepository) {
    suspend operator fun invoke(server: Server) {
        repository.addServer(server)
    }
}
