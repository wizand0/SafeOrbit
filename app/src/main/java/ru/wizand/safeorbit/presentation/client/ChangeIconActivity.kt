package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import ru.wizand.safeorbit.databinding.ActivityChangeIconBinding
import java.io.File
import java.io.FileOutputStream

class ChangeIconActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangeIconBinding
    private lateinit var viewModel: ChangeIconViewModel

    private lateinit var clientViewModel: ClientViewModel

    private lateinit var serverId: String
    private var selectedBitmap: Bitmap? = null
    private lateinit var cameraUri: Uri

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processImage(it) }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            processImage(cameraUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangeIconBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ChangeIconViewModel::class.java]
        clientViewModel = ViewModelProvider(this)[ClientViewModel::class.java]

        serverId = intent.getStringExtra("serverId") ?: run {
            finish()
            return
        }

        loadExistingIconFromViewModel()

        binding.buttonPickImage.setOnClickListener {
            showSourceDialog()
        }

        binding.buttonSaveIcon.setOnClickListener {
            val path = saveIconToStorage(serverId)
            if (path != null) {
                viewModel.saveIcon(serverId, path) {
                    clientViewModel.refreshIcon(serverId)
                    Snackbar.make(binding.root, "Иконка сохранена", Snackbar.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1200)
                }
            } else {
                Toast.makeText(this, "Сначала выберите изображение", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSourceDialog() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи")
        AlertDialog.Builder(this)
            .setTitle("Выберите источник")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> pickImage.launch("image/*")
                }
            }.show()
    }

    private fun launchCamera() {
        val imageFile = File.createTempFile("server_icon_", ".jpg", cacheDir)
        cameraUri = FileProvider.getUriForFile(this, "$packageName.provider", imageFile)
        takePhoto.launch(cameraUri)
    }

    private fun processImage(uri: Uri) {
        val original = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
        val cropped = cropToSquare(original)
        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)

        selectedBitmap = scaled
        binding.imagePreview.setImageBitmap(scaled)
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2

        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, Rect(xOffset, yOffset, xOffset + size, yOffset + size), Rect(0, 0, size, size), null)
        return result
    }

    private fun saveIconToStorage(serverId: String): String? {
        val bitmap = selectedBitmap ?: return null
        val file = File(filesDir, "icon_$serverId.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        return file.absolutePath
    }

    private fun loadExistingIconFromViewModel() {
        val uri = clientViewModel.iconUriMap.value?.get(serverId)?.let { Uri.parse(it) }
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedBitmap = bitmap
                binding.imagePreview.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Попробуем локальный файл на всякий случай
            val file = File(filesDir, "icon_$serverId.jpg")
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                selectedBitmap = bitmap
                binding.imagePreview.setImageBitmap(bitmap)
            }
        }
    }
}
