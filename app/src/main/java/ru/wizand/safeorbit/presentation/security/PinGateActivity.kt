package ru.wizand.safeorbit.presentation.security

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.wizand.safeorbit.databinding.ActivityPinGateBinding
import ru.wizand.safeorbit.presentation.server.ServerMainActivity
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class PinGateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinGateBinding
    private var savedPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinGateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        savedPin = prefs.getString("server_pin", null)

        if (savedPin == null) {
            binding.btnVerifyPin.text = "Установить PIN"
        }

        binding.btnVerifyPin.setOnClickListener {
            val input = binding.etPinVerify.text.toString()
            if (input.length < 4) {
                Toast.makeText(this, "Введите хотя бы 4 цифры", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (savedPin == null) {
                prefs.edit()
                    .putString("server_pin", input)
                    .putBoolean("pin_verified", true)
                    .apply()
                startActivity(Intent(this, ServerMainActivity::class.java))
                finish()
            } else if (input == savedPin) {
                prefs.edit()
                    .putBoolean("pin_verified", true)
                    .apply()
                startActivity(Intent(this, ServerMainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
