package ru.wizand.safeorbit.presentation.security

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.server.ServerMainActivity

class PinVerificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_verification)

        val etPin = findViewById<EditText>(R.id.etPinVerify)
        val btnVerify = findViewById<Button>(R.id.btnVerifyPin)

        val savedPin = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("server_pin", null)

        btnVerify.setOnClickListener {
            val entered = etPin.text.toString()
            if (entered == savedPin) {
                startActivity(Intent(this, ServerMainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
