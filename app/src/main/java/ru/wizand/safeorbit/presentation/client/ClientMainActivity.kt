package ru.wizand.safeorbit.presentation.client

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.databinding.ActivityClientMainBinding
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class ClientMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientMainBinding
    private val viewModel: ClientViewModel by viewModels()
    private var selectedItemId: Int = R.id.nav_map

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверка роли
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentRole = prefs.getString("user_role", null)
        if (currentRole != UserRole.CLIENT.name) {
            Log.d("DEBUG", "ClientMainActivity role mismatch, finishing")
            finish()
            return
        }

        binding = ActivityClientMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedItemId = savedInstanceState?.getInt("selected_nav_item") ?: R.id.nav_map
        setupBottomNavigation()

        if (savedInstanceState == null) {
            showFragmentById(selectedItemId)
        } else {
            binding.bottomNavigation.selectedItemId = selectedItemId
        }

        viewModel.loadAndObserveServers()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId != selectedItemId) {
                selectedItemId = item.itemId
                showFragmentById(item.itemId)
            }
            true
        }
    }

    private fun showFragmentById(itemId: Int) {
        val fragment = when (itemId) {
            R.id.nav_map -> MapFragment()
            R.id.nav_servers -> ServerListFragment()
            R.id.nav_placeholder -> PlaceholderFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> MapFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_nav_item", selectedItemId)
    }
}
