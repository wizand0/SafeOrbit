package ru.wizand.safeorbit.presentation.server.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ru.wizand.safeorbit.databinding.ActivityAudioLaunchBinding
import ru.wizand.safeorbit.R

class AudioLaunchActivity : ComponentActivity() {

    private lateinit var binding: ActivityAudioLaunchBinding
    private val REQUEST_AUDIO_PERMISSIONS = 4001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startMicAnimation()
        requestPermissionsIfNeeded()
    }

    private fun startMicAnimation() {
        val animation = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.imageMic.startAnimation(animation)
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startAudioService()
        } else {
            ActivityCompat.requestPermissions(
                this,
                notGranted.toTypedArray(),
                REQUEST_AUDIO_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startAudioService()
        } else {
            finish() // Закрываем активити, если отказ
        }
    }

    private fun startAudioService() {
        val serverId = intent.getStringExtra("server_id") ?: return finish()
        // Agola
        val intent = Intent(this, AudioBroadcastService::class.java).apply {
            // LiveKit
//        val intent = Intent(this, AudioBroadcastServiceLiveKit::class.java).apply {
            putExtra("server_id", serverId)
        }
        ContextCompat.startForegroundService(this, intent)
        finish()
    }
}
