package ru.wizand.safeorbit.domain.model

data class Server(
    val serverId: String,
    val pairingToken: String,  // используем pairingToken вместо кода
    val name: String,
    val iconUri: String?
)