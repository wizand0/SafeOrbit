package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.firebase.FirebaseRepository

class RequestAudioUseCase(private val firebaseRepository: FirebaseRepository) {
    fun execute(serverId: String) {
        firebaseRepository.sendAudioRequest(serverId)
    }
}
