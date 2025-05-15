package ru.wizand.safeorbit.presentation.client

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityClientMainBinding
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity

class ClientMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityClientMainBinding
    private val viewModel: ClientViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            val serverId = binding.editServerId.text.toString()
            val code = binding.editCode.text.toString()
            viewModel.pairWithServer(serverId, code)
        }

        binding.btnResetRole.setOnClickListener {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().remove("user_role").apply()
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finish()
        }

        viewModel.pairingResult.observe(this) { success ->
            if (success) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, MapFragment())
                    .commit()
            } else {
                Toast.makeText(this, "Ошибка подключения", Toast.LENGTH_SHORT).show()
            }
        }
    }
}