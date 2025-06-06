package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityChangeIconBinding
import java.io.File

class ChangeIconActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangeIconBinding
    private lateinit var viewModel: ChangeIconViewModel
    private lateinit var clientViewModel: ClientViewModel

    private lateinit var serverId: String
    private var selectedBitmap: android.graphics.Bitmap? = null
    private lateinit var cameraUri: Uri

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.processImage(this, it.toString()) }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            viewModel.processImage(this, cameraUri.toString())
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

        binding.buttonPickImage.setOnClickListener { showSourceDialog() }

        binding.buttonSaveIcon.setOnClickListener {
            val bitmap = selectedBitmap
            if (bitmap != null) {
                val path = viewModel.saveIconToStorage(this, serverId, bitmap)
                if (path != null) {
                    viewModel.saveIcon(serverId, path) {
                        clientViewModel.refreshIcon(serverId)
                        Snackbar.make(binding.root, getString(R.string.icon_saved), Snackbar.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1200)
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.choose_image), Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.processedBitmap.observe(this) { bitmap ->
            selectedBitmap = bitmap
            binding.imagePreview.setImageBitmap(bitmap)
        }

        loadExistingIconFromViewModel()
    }

    private fun showSourceDialog() {
        val options = arrayOf(getString(R.string.make_photo), getString(R.string.choose_from_gallery))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_from))
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

    private fun loadExistingIconFromViewModel() {
        val uri = clientViewModel.iconUriMap.value?.get(serverId)?.let { Uri.parse(it) }
        if (uri != null) {
            try {
                viewModel.processImage(this, uri.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val file = File(filesDir, "icon_$serverId.jpg")
            if (file.exists()) {
                viewModel.processImage(this, Uri.fromFile(file).toString())
            }
        }
    }
}
