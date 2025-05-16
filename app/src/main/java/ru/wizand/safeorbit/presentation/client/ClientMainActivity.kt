package ru.wizand.safeorbit.presentation.client

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import ru.wizand.safeorbit.databinding.ActivityClientMainBinding

class ClientMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientMainBinding

    private val viewModel: ClientViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.container.id, MapFragment())
                .commit()
        }

        binding.btnAddServer.setOnClickListener {
            AddServerDialogFragment { serverId, code, name ->
                viewModel.addServer(serverId, code, name)
                viewModel.loadAndObserveServers()
            }.show(supportFragmentManager, "AddServerDialog")
        }

        binding.btnAllServers.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(binding.container.id, ServerListFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnSettings.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(binding.container.id, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
//        viewModel.loadAndObserveServers() // только подгружаем данные
    }
}
