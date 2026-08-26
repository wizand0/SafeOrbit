package ru.wizand.safeorbit.data.model

data class ServerInfo(
    val serverId: String = "",
    val pairingToken: String = ""  // используем pairingToken вместо кода
)
