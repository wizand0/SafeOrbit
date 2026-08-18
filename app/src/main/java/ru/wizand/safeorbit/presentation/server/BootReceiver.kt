package ru.wizand.safeorbit.presentation.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "📱 Устройство перезагрузилось. Запускаем сервис")

            // Фоновый статус: Toast не показываем — только тихий лог.
            // Пользователь видит состояние через foreground-уведомление сервиса
            // в канале location_service_channel.
            val serviceIntent = Intent(context, LocationService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
