package ru.wizand.safeorbit.data

enum class InactivityTimeout(val millis: Long) {
    MINUTES_3(3 * 60 * 1000),
    MINUTES_5(5 * 60 * 1000),
    MINUTES_10(10 * 60 * 1000),
    MINUTES_30(30 * 60 * 1000);
}