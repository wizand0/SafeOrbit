package ru.wizand.safeorbit.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY date DESC, startHour DESC")
    fun getAllLogs(): LiveData<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
