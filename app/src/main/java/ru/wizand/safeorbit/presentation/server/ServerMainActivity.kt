package ru.wizand.safeorbit.presentation.server

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.AudioRequest
import ru.wizand.safeorbit.databinding.ActivityServerMainBinding
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity

@AndroidEntryPoint
class ServerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerMainBinding
    private lateinit var viewModel: ServerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ServerViewModel::class.java]

        viewModel.registerServer()

        viewModel.serverId.observe(this) { serverId ->
            binding.tvServerId.text = "ID сервера: $serverId"
            startLocationService() // запуск сервиса отслеживания
        }

        viewModel.code.observe(this) { code ->
            binding.tvPairCode.text = "Код подключения: $code"
        }

        viewModel.audioRequest.observe(this) { request ->
            if (request != null) {
                Toast.makeText(this, "Запрос на запись от клиента", Toast.LENGTH_SHORT).show()
                handleAudioRequest(request)
            }
        }

        binding.btnResetRole.setOnClickListener {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().remove("user_role").apply()
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finish()
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun handleAudioRequest(request: AudioRequest) {
        // В этом месте должна быть логика:
        // 1. Запись аудио (MediaRecorder или AudioRecord)
        // 2. Сохранение в файл
        // 3. Загрузка файла в Firebase Storage
        // 4. Сохранение ссылки клиенту

        // Пока просто заглушка
        Toast.makeText(this, "Начинаем запись звука...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, LocationService::class.java))
    }
}
