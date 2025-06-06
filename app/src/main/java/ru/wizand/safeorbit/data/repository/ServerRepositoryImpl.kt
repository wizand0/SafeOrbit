package ru.wizand.safeorbit.data.repository

import ru.wizand.safeorbit.data.ServerEntity
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.domain.model.Server
import ru.wizand.safeorbit.domain.repository.ServerRepository

class ServerRepositoryImpl(
    private val db: AppDatabase
) : ServerRepository {

    override suspend fun addServer(server: Server) {
        val entity = ServerEntity(
            id = 0,
            serverId = server.serverId,
            code = server.code,
            name = server.name,
            serverIconUri = server.iconUri
        )
        db.serverDao().insert(entity)
    }

    override suspend fun getServers(): List<Server> {
        return db.serverDao().getAll().map {
            Server(it.serverId, it.code, it.name, it.serverIconUri)
        }
    }
}
