package ru.wizand.safeorbit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,             // "2025-05-18"
    val startHour: Int,           // 11
    val endHour: Int,             // 12
    val mode: String,             // "Активность" или "ЭКОНОМ"
    val steps: Int?,              // может быть null для режима ЭКОНОМ
    val distanceMeters: Float?    // может быть null для режима ЭКОНОМ
)