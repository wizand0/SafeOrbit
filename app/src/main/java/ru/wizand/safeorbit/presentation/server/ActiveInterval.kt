package ru.wizand.safeorbit.presentation.server

enum class ActiveInterval(val label: String, val millis: Long) {
    SECONDS_5("5 сек", 5_000L),
    SECONDS_15("15 сек", 15_000L),
    SECONDS_30("30 сек", 30_000L),
    SECONDS_60("60 сек", 60_000L),
    SECONDS_120("120 сек", 2 * 60_000L);

    override fun toString(): String = label

    companion object {
        fun fromMillis(millis: Long): ActiveInterval {
            return values().find { it.millis == millis } ?: SECONDS_30
        }
    }
}
