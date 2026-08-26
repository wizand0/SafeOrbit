package ru.wizand.safeorbit.presentation.server

import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager
import ru.wizand.safeorbit.databinding.ActivityServerSettingsBinding
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class ServerSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerSettingsBinding
    private lateinit var encryptedPrefs: EncryptedPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        encryptedPrefs = EncryptedPreferencesManager.getInstance(this)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupActiveSpinner(prefs)
        setupInactivitySpinner(prefs)

        // Два клика
//        binding.spinnerActive.setOnClickListener {
//            binding.spinnerActive.showDropDown()
//        }
//        binding.spinnerInactivity.setOnClickListener {
//            binding.spinnerInactivity.showDropDown()
//        }

        // Один клик
        binding.spinnerActive.setOnTouchListener { v, event ->
            v.performClick() // Важно для доступности
            binding.spinnerActive.showDropDown()
            false
        }
        binding.spinnerInactivity.setOnTouchListener { v, event ->
            v.performClick() // Важно для доступности
            binding.spinnerInactivity.showDropDown()
            false
        }




        binding.btnNotificationSources.setOnClickListener {
            startActivity(Intent(this, NotificationSourcesActivity::class.java))
        }

        binding.btnConnectionInfo.setOnClickListener {
            showConnectionInfoDialog()
        }

        var savedPin = encryptedPrefs.hasPin()

        binding.btnCheckPin.setOnClickListener {
            val enteredPin = binding.etPin.text.toString()
            if (enteredPin.length < 4) {
                Toast.makeText(this, getString(R.string.need_at_list_4_digits), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!savedPin) {
                encryptedPrefs.savePin(enteredPin)
                savedPin = true
                Toast.makeText(this, getString(R.string.pin_istalled), Toast.LENGTH_SHORT).show()
                showSettings()
            } else if (encryptedPrefs.verifyPin(enteredPin)) {
                showSettings()
            } else {
                Toast.makeText(this, getString(R.string.wrong_pin), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangePin.setOnClickListener { showChangePinDialog() }

        binding.btnResetRole.setOnClickListener {
            stopService(Intent(this, LocationService::class.java))

            encryptedPrefs.clearAll()
            prefs.edit()
                .remove("permissions_intro_shown")
                .apply()

            startActivity(Intent(this, RoleSelectionActivity::class.java).apply {
                putExtra("fromReset", true)
            })
            finishAffinity()
        }
    }

    private fun showSettings() {
        binding.etPin.visibility = View.GONE
        binding.btnCheckPin.visibility = View.GONE
        binding.settingsContent.visibility = View.VISIBLE
    }

    private fun setupInactivitySpinner(prefs: SharedPreferences) {
        val timeoutOptions = InactivityTimeout.values()
        val adapter = ArrayAdapter(this, R.layout.dropdown_menu_popup_item, timeoutOptions)
        binding.spinnerInactivity.setAdapter(adapter)

        val savedMillis = prefs.getLong("inactivity_timeout", InactivityTimeout.MINUTES_5.millis)
        val selectedOption = InactivityTimeout.fromMillis(savedMillis)
        binding.spinnerInactivity.setText(selectedOption.toString(), false)

        binding.spinnerInactivity.setOnItemClickListener { _, _, position, _ ->
            val newTimeout = timeoutOptions[position].millis
            prefs.edit().putLong("inactivity_timeout", newTimeout).apply()
            Snackbar.make(binding.spinnerInactivity,
                getString(R.string.interval_changed), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupActiveSpinner(prefs: SharedPreferences) {
        val activeOptions = ActiveInterval.values() // вместо entries
        val adapter = ArrayAdapter(this, R.layout.dropdown_menu_popup_item, activeOptions)
        binding.spinnerActive.setAdapter(adapter)

        val savedMillis = prefs.getLong("active_interval", ActiveInterval.SECONDS_30.millis)
        val selectedOption = ActiveInterval.fromMillis(savedMillis)
        binding.spinnerActive.setText(selectedOption.toString(), false)

        binding.spinnerActive.setOnItemClickListener { _, _, position, _ ->
            val newInterval = activeOptions[position].millis
            prefs.edit().putLong("active_interval", newInterval).apply()
            Snackbar.make(binding.spinnerActive,
                getString(R.string.active_interval_changed), Snackbar.LENGTH_SHORT).show()
        }
    }


    private fun showConnectionInfoDialog() {
        val serverId = encryptedPrefs.getServerId()
        val pairingToken = encryptedPrefs.getPairingToken()  // используем pairingToken вместо кода

        if (serverId.isNullOrBlank() || pairingToken.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.server_not_registered), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = ru.wizand.safeorbit.databinding.DialogConnectionInfoBinding.inflate(layoutInflater)
        dialogBinding.tvConnectionCode.text = pairingToken  // отображаем pairingToken вместо кода

        val data = "$serverId|$pairingToken"  // используем pairingToken в QR-коде
        val matrix = com.google.zxing.MultiFormatWriter().encode(data, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
        dialogBinding.ivConnectionQr.setImageBitmap(com.journeyapps.barcodescanner.BarcodeEncoder().createBitmap(matrix))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.connection_info_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.button_continue), null)
            .show()
    }

    private fun showChangePinDialog() {
        // 1.7 (аудит): сравнение через verifyPin, plaintext наружу не отдаётся

        val dialogBinding = ru.wizand.safeorbit.databinding.DialogChangePinBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.Changing_pin))
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val oldPinInput = dialogBinding.etOldPin.text.toString()
                val newPinInput = dialogBinding.etNewPin.text.toString()

                if (encryptedPrefs.verifyPin(oldPinInput) && newPinInput.length >= 4) {
                    encryptedPrefs.savePin(newPinInput)
                    Toast.makeText(this, getString(R.string.pin_renewed), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this,
                        getString(R.string.old_pin_or_new_pin_wrong), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

}

