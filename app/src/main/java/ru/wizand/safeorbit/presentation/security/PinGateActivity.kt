package ru.wizand.safeorbit.presentation.security

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.server.ServerMainActivity

class PinGateActivity : AppCompatActivity() {

    private lateinit var etPin: EditText
    private lateinit var btnConfirm: Button
    private var savedPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_gate)

        etPin = findViewById(R.id.etPinVerify)
        btnConfirm = findViewById(R.id.btnVerifyPin)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        savedPin = prefs.getString("server_pin", null)

        if (savedPin == null) {
            btnConfirm.text = "Установить PIN"
        }

        btnConfirm.setOnClickListener {
            val input = etPin.text.toString()
            if (input.length < 4) {
                Toast.makeText(this, "Введите хотя бы 4 цифры", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (savedPin == null) {
                prefs.edit()
                    .putString("server_pin", input)
                    .putBoolean("pin_verified", true) // ✅ запоминаем успешную проверку
                    .apply()
                startActivity(Intent(this, ServerMainActivity::class.java))
                finish()
            } else if (input == savedPin) {
                prefs.edit()
                    .putBoolean("pin_verified", true) // ✅ запоминаем успешную проверку
                    .apply()
                startActivity(Intent(this, ServerMainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
