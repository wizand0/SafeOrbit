package ru.wizand.safeorbit.presentation.client

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.ServerEntity
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.databinding.ActivityClientMainBinding

class ClientMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientMainBinding
    private lateinit var db: AppDatabase

    private var alreadyObserving = false

    private val viewModel: ClientViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        // Открываем MapFragment по умолчанию
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.container.id, MapFragment())
                .commit()
        }

        binding.btnAddServer.setOnClickListener {
            AddServerDialogFragment { serverId, code, name ->
                lifecycleScope.launch {
                    db.serverDao()
                        .insert(ServerEntity(serverId = serverId, code = code, name = name))
                    observeAllServers() // обновим карту
                }
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

        observeAllServers()
    }

    override fun onResume() {
        super.onResume()
        observeAllServers()
    }

    private fun observeAllServers() {
        if (alreadyObserving) return
        alreadyObserving = true

        lifecycleScope.launch {
            val servers = db.serverDao().getAll()
            val serverIds = servers.map { it.serverId }
            val nameMap = servers.associateBy({ it.serverId }, { it.name })

            viewModel.observeAllServerLocations(serverIds)

            viewModel.allServerLocations.observe(this@ClientMainActivity) { locationMap ->
                val fragment = supportFragmentManager.findFragmentById(binding.container.id)
                if (fragment is MapFragment) {
                    fragment.updateMarkers(locationMap, nameMap)
                }
            }
        }
    }


}
