package ru.wizand.safeorbit.presentation.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("ru.wizand.safeorbit.presentation.server.BootReceiver", "📱 Устройство перезагрузилось. Запускаем сервис")

            // Пример: запуск LocationService после загрузки
            val serviceIntent = Intent(context, LocationService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Toast.makeText(context, "SafeOrbit: Сервис запущен после перезагрузки", Toast.LENGTH_SHORT).show()
        }
    }
}