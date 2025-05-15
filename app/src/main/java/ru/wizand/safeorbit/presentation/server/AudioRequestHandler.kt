package ru.wizand.safeorbit.presentation.server

import android.content.Context
import android.widget.Toast
import ru.wizand.safeorbit.data.model.AudioRequest

class AudioRequestHandler(private val context: Context) {

    fun handle(request: AudioRequest) {
        // TODO: Реализовать запись, сохранение и загрузку аудио
        Toast.makeText(context, "Начинаем запись звука...", Toast.LENGTH_SHORT).show()
    }
}
