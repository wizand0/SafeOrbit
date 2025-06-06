package ru.wizand.safeorbit.domain.model

data class Server(
    val serverId: String,
    val code: String,
    val name: String,
    val iconUri: String?
)