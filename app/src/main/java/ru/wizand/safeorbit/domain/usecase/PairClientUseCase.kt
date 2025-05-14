package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.firebase.FirebaseRepository

class PairClientUseCase(private val firebaseRepository: FirebaseRepository) {
    fun execute(serverId: String, code: String, onResult: (Boolean) -> Unit) {
        firebaseRepository.pairClientToServer(serverId, code, onResult)
    }
}
