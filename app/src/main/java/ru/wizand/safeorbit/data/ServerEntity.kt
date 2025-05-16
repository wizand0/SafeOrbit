package ru.wizand.safeorbit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: String,
    val code: String,
    val name: String = "Без названия",
    val serverIconUri: String? = null
)