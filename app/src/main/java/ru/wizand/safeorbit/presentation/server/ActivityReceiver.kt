package ru.wizand.safeorbit.presentation.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import ru.wizand.safeorbit.utils.LocationLogger

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            LocationLogger.debug("Событие активности: $event")

            val serviceIntent = Intent(context, LocationService::class.java).apply {
                putExtra("activity_type", event.activityType)
                putExtra("transition_type", event.transitionType)
            }
            context.startService(serviceIntent)
        }
    }
}
