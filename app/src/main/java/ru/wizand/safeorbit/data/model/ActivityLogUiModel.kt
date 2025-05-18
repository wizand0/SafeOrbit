package ru.wizand.safeorbit.data.model

data class ActivityLogUiModel(
    val date: String,
    val startHour: Int,
    val endHour: Int,
    val mode: String,
    val steps: Int? = null,
    val distanceMeters: Float? = null
)
