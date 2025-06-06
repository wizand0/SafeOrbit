package ru.wizand.safeorbit.presentation.client


import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase
import java.io.File
import java.io.FileOutputStream

class ChangeIconViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    private val _processedBitmap = MutableLiveData<Bitmap>()
    val processedBitmap: LiveData<Bitmap> = _processedBitmap

    fun processImage(context: Context, uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                val original = BitmapFactory.decodeStream(inputStream)
                val cropped = cropToSquare(original)
                val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                _processedBitmap.postValue(scaled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2

        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(
            bitmap,
            Rect(xOffset, yOffset, xOffset + size, yOffset + size),
            Rect(0, 0, size, size),
            null
        )
        return result
    }

    fun saveIconToStorage(context: Context, serverId: String, bitmap: Bitmap): String? {
        return try {
            val file = File(context.filesDir, "icon_$serverId.jpg")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveIcon(serverId: String, localPath: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId)
            server?.let {
                db.serverDao().insert(it.copy(serverIconUri = localPath))
                onComplete()
            }
        }
    }
}