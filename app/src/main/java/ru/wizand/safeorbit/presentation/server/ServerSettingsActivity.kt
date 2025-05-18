// Изменённый файл ServerSettingsActivity.kt
// Добавлено: настройка периода бездействия

package ru.wizand.safeorbit.presentation.server

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity

class ServerSettingsActivity : AppCompatActivity() {

    private lateinit var etPin: EditText
    private lateinit var btnCheckPin: Button
    private lateinit var settingsContent: LinearLayout
    private lateinit var btnResetRole: Button
    private lateinit var btnChangePin: Button
    private lateinit var spinnerInactivity: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_settings)

        etPin = findViewById(R.id.etPin)
        btnCheckPin = findViewById(R.id.btnCheckPin)
        settingsContent = findViewById(R.id.settingsContent)
        btnResetRole = findViewById(R.id.btnResetRole)
        btnChangePin = findViewById(R.id.btnChangePin)
        spinnerInactivity = findViewById(R.id.spinnerInactivity)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        var savedPin = prefs.getString("server_pin", null)

        btnCheckPin.setOnClickListener {
            val enteredPin = etPin.text.toString()
            if (enteredPin.length < 4) {
                Toast.makeText(this, "Введите хотя бы 4 цифры", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (savedPin == null) {
                prefs.edit().putString("server_pin", enteredPin).apply()
                savedPin = enteredPin
                Toast.makeText(this, "PIN установлен", Toast.LENGTH_SHORT).show()
                showSettings()
            } else if (enteredPin == savedPin) {
                showSettings()
            } else {
                Toast.makeText(this, "Неверный PIN", Toast.LENGTH_SHORT).show()
            }
        }

        btnChangePin.setOnClickListener { showChangePinDialog() }
        btnResetRole.setOnClickListener {
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                .remove("user_role")
                .remove("pin_verified")
                .apply()
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            finishAffinity()
        }

        val timeoutOptions = listOf("3 мин", "5 мин", "10 мин", "30 мин")
        val timeoutValues = listOf(3L, 5L, 10L, 30L).map { it * 60 * 1000 }
        val savedTimeout = prefs.getLong("inactivity_timeout", 5 * 60 * 1000L)

        spinnerInactivity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeoutOptions)
        spinnerInactivity.setSelection(timeoutValues.indexOf(savedTimeout))

        spinnerInactivity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putLong("inactivity_timeout", timeoutValues[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showSettings() {
        etPin.visibility = EditText.GONE
        btnCheckPin.visibility = Button.GONE
        settingsContent.visibility = LinearLayout.VISIBLE
    }

    private fun showChangePinDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentPin = prefs.getString("server_pin", null)

        val dialogView = layoutInflater.inflate(R.layout.dialog_change_pin, null)
        val etOldPin = dialogView.findViewById<EditText>(R.id.etOldPin)
        val etNewPin = dialogView.findViewById<EditText>(R.id.etNewPin)

        AlertDialog.Builder(this)
            .setTitle("Изменение PIN")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val oldPinInput = etOldPin.text.toString()
                val newPinInput = etNewPin.text.toString()

                if (oldPinInput == currentPin && newPinInput.length >= 4) {
                    prefs.edit().putString("server_pin", newPinInput).apply()
                    Toast.makeText(this, "PIN обновлён", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Неверный старый PIN или новый PIN слишком короткий", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
