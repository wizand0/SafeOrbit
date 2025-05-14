package ru.wizand.safeorbit.presentation.role

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.databinding.ActivityRoleSelectionBinding
import ru.wizand.safeorbit.presentation.client.ClientMainActivity
import ru.wizand.safeorbit.presentation.server.ServerMainActivity

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding
    private val viewModel: RoleSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        binding.btnServer.setOnClickListener {
            viewModel.saveUserRole(UserRole.SERVER)
            startActivity(Intent(this, ServerMainActivity::class.java))
        }

        binding.btnClient.setOnClickListener {
            viewModel.saveUserRole(UserRole.CLIENT)
            startActivity(Intent(this, ClientMainActivity::class.java))
        }
    }
}