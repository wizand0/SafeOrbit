package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.ServerInfo

class RegisterServerUseCase(private val firebaseRepository: FirebaseRepository) {
    fun execute(onComplete: (ServerInfo) -> Unit) {
        firebaseRepository.registerServer { id, code ->
            onComplete(ServerInfo(serverId = id, code = code))
        }
    }
}
