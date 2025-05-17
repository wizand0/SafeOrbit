package ru.wizand.safeorbit.presentation.server

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityServerMainBinding

class ServerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerMainBinding

    private var selectedItemId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedItemId = savedInstanceState?.getInt("selected_nav_item") ?: R.id.nav_home

        setupBottomNavigation()

        if (savedInstanceState == null) {
            showFragmentById(selectedItemId)
        } else {
            binding.serverBottomNavigation.selectedItemId = selectedItemId
        }
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.serverBottomNavigation)
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
            R.id.nav_home -> ServerMainFragment() // главный экран
            R.id.nav_placeholder -> ServerPlaceholderFragment() // второй экран
            R.id.nav_settings -> {
                startActivity(Intent(this, ServerSettingsActivity::class.java))
                return
            }// или переход в активити, если надо
            else -> ServerMainFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.serverFragmentContainer, fragment)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_nav_item", selectedItemId)
    }
}
