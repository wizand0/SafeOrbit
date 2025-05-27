package ru.wizand.safeorbit.presentation.server

enum class ActiveInterval(val label: String, val millis: Long) {
    SECONDS_5("5 sec", 5_000L),
    SECONDS_15("15 sec", 15_000L),
    SECONDS_30("30 sec", 30_000L),
    SECONDS_60("60 sec", 60_000L),
    SECONDS_120("120 sec", 2 * 60_000L);

    override fun toString(): String = label

    companion object {
        fun fromMillis(millis: Long): ActiveInterval {
            return values().find { it.millis == millis } ?: SECONDS_30
        }
    }
}
