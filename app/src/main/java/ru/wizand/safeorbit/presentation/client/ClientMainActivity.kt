package ru.wizand.safeorbit.presentation.client

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityClientMainBinding

class ClientMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClientMainBinding
    private val viewModel: ClientViewModel by viewModels()

    // Сохраняем выбранный пункт меню
    private var selectedItemId: Int = R.id.nav_map

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedItemId = savedInstanceState?.getInt("selected_nav_item") ?: R.id.nav_map

        setupBottomNavigation()
//        setupFab()

        if (savedInstanceState == null) {
            showFragmentById(selectedItemId)
        } else {
            binding.bottomNavigation.selectedItemId = selectedItemId
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAndObserveServers()
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        nav.setOnItemSelectedListener { item ->
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
