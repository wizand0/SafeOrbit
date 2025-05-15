package ru.wizand.safeorbit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers")
    suspend fun getAll(): List<ServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: ServerEntity)

    @Delete
    suspend fun delete(server: ServerEntity)
}