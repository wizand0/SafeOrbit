package ru.wizand.safeorbit.presentation.security

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.wizand.safeorbit.databinding.ActivityPinVerificationBinding
import ru.wizand.safeorbit.presentation.server.ServerMainActivity
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class PinVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinVerificationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedPin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString("server_pin", null)

        binding.btnVerifyPin.setOnClickListener {
            val entered = binding.etPinVerify.text.toString()
            if (entered == savedPin) {
                startActivity(Intent(this, ServerMainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
