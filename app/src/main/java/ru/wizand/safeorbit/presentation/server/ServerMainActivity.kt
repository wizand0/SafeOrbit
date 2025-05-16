package ru.wizand.safeorbit.presentation.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.databinding.ActivityServerMainBinding
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity

@AndroidEntryPoint
class ServerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerMainBinding
    private lateinit var viewModel: ServerViewModel
    private lateinit var permissionManager: LocationPermissionManager
    private lateinit var audioRequestHandler: AudioRequestHandler

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("latitude", 0.0) ?: 0.0
            val lon = intent?.getDoubleExtra("longitude", 0.0) ?: 0.0
            binding.tvCurrentCoords.text = "Координаты: $lat, $lon"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ServerViewModel::class.java]
        permissionManager = LocationPermissionManager(this) {
            startLocationService()
        }
        audioRequestHandler = AudioRequestHandler(this)



        viewModel.serverId.observe(this) { serverId ->
            binding.tvServerId.text = "ID сервера: $serverId"
            permissionManager.checkOrRequestLocationPermissions()
            Log.d("AUTH", "ID сервера: $serverId")
        }

        viewModel.code.observe(this) { code ->
            binding.tvPairCode.text = "Код подключения: $code"
            Log.d("AUTH", "Код подключения: $code")
        }

        viewModel.audioRequest.observe(this) { request ->
            request?.let {
                Toast.makeText(this, "Запрос на запись от клиента", Toast.LENGTH_SHORT).show()
                audioRequestHandler.handle(it)
            }
        }

        binding.btnResetRole.setOnClickListener {
            viewModel.reset() // очищаем сохранённый ID
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit().remove("user_role").apply()
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handlePermissionsResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, LocationService::class.java))
    }


    override fun onResume() {
        super.onResume()
        Log.d("ACTIVITY", "onResume — регистрируем locationReceiver")
        val filter = IntentFilter("LOCATION_UPDATE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, filter)
        } else {
            registerReceiver(locationReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
    }

    private fun startLocationService() {
        val serverId = viewModel.serverId.value ?: return
        val intent = Intent(this, LocationService::class.java).apply {
            putExtra("server_id", serverId)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}