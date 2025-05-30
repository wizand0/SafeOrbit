package ru.wizand.safeorbit.presentation.server

enum class InactivityTimeout(val label: String, val millis: Long) {
    MINUTES_3("3 min", 3 * 60 * 1000L),
    MINUTES_5("5 min", 5 * 60 * 1000L),
    MINUTES_10("10 min", 10 * 60 * 1000L),
    MINUTES_30("30 min", 30 * 60 * 1000L),
    MINUTES_60("60 min", 60 * 60 * 1000L),
    MINUTES_120("120 min", 2 * 60 * 60 * 1000L);

    override fun toString(): String = label

    companion object {
        fun fromMillis(millis: Long): InactivityTimeout {
            return values().find { it.millis == millis } ?: MINUTES_5
        }
    }
}
