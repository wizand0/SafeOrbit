package ru.wizand.safeorbit.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object NavigationUtils {

    fun openNavigationChooser(
        context: Context,
        latitude: Double,
        longitude: Double,
        name: String = "Точка назначения"
    ) {
        // Формируем geo: URI
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($name)")

        // Строим интент без setPackage
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // Проверка доступности хотя бы одного обработчика
        val pm = context.packageManager
        if (intent.resolveActivity(pm) != null) {
            context.startActivity(Intent.createChooser(intent, "Открыть с помощью"))
        } else {
            Toast.makeText(
                context,
                "Нет подходящего приложения для навигации",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
