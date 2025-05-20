package ru.wizand.safeorbit.data.model

data class ActivityLogUiModel(
    val date: String,
    val startHour: Int = -1, // ⬅️ значение по умолчанию
    val endHour: Int = -1,
    val mode: String = "",
    val steps: Int? = null,
    val distanceMeters: Float? = null,
    val dailySteps: Int? = null,
    val isSummary: Boolean = false
)

