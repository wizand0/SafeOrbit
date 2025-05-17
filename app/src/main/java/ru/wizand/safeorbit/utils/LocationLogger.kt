package ru.wizand.safeorbit.utils

import android.util.Log

object LocationLogger {

    private const val PREFIX = "LOCATION_DEBUG"

    fun info(message: String) {
        Log.i(PREFIX, message)
    }

    fun debug(message: String) {
        Log.d(PREFIX, message)
    }

    fun warn(message: String) {
        Log.w(PREFIX, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(PREFIX, message, throwable)
        } else {
            Log.e(PREFIX, message)
        }
    }
}
