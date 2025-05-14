package ru.wizand.safeorbit.domain.usecase

import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.data.model.LocationData

class SendLocationUseCase(private val firebaseRepository: FirebaseRepository) {
    fun execute(serverId: String, locationData: LocationData) {
        firebaseRepository.sendLocation(serverId, locationData)
    }
}
