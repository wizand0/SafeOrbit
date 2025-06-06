package ru.wizand.safeorbit.domain.repository

import ru.wizand.safeorbit.domain.model.Server

interface ServerRepository {
    suspend fun addServer(server: Server)
    suspend fun getServers(): List<Server>
}
