package ru.wizand.safeorbit.presentation.server

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityServerSettingsBinding
import ru.wizand.safeorbit.device.MyDeviceAdminReceiver
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class ServerSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerSettingsBinding
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val REQUEST_CODE_ENABLE_ADMIN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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



        // Init Device Policy
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        binding.btnEnableAdmin.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.need_for_save_app_from_deleting))
                }
                startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            } else {
                Toast.makeText(this, getString(R.string.app_is_admin), Toast.LENGTH_SHORT).show()
            }
        }

        var savedPin = prefs.getString("server_pin", null)

        binding.btnCheckPin.setOnClickListener {
            val enteredPin = binding.etPin.text.toString()
            if (enteredPin.length < 4) {
                Toast.makeText(this, getString(R.string.need_at_list_4_digits), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (savedPin == null) {
                prefs.edit().putString("server_pin", enteredPin).apply()
                savedPin = enteredPin
                Toast.makeText(this, getString(R.string.pin_istalled), Toast.LENGTH_SHORT).show()
                showSettings()
            } else if (enteredPin == savedPin) {
                showSettings()
            } else {
                Toast.makeText(this, getString(R.string.wrong_pin), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangePin.setOnClickListener { showChangePinDialog() }

        binding.btnResetRole.setOnClickListener {
            stopService(Intent(this, LocationService::class.java))

            prefs.edit()
                .remove("user_role")
                .remove("pin_verified")
                .remove("server_pin")
                .remove("permissions_intro_shown")
                .apply()

            startActivity(Intent(this, RoleSelectionActivity::class.java).apply {
                putExtra("fromReset", true)
            })
            finishAffinity()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            val message = if (resultCode == Activity.RESULT_OK) {
                getString(R.string.admin_permission_set)
            } else {
                getString(R.string.admin_permission_not_set)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettings() {
        binding.etPin.visibility = View.GONE
        binding.btnCheckPin.visibility = View.GONE
        binding.settingsContent.visibility = View.VISIBLE
    }

    private fun setupInactivitySpinner(prefs: SharedPreferences) {
        val timeoutOptions = InactivityTimeout.values()
        Log.d("SPINNER_DEBUG", "Active options: ${timeoutOptions.joinToString()}")
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
        Log.d("SPINNER_DEBUG", "Active options: ${activeOptions.joinToString()}")
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


    private fun showChangePinDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val currentPin = prefs.getString("server_pin", null)

        val dialogBinding = ru.wizand.safeorbit.databinding.DialogChangePinBinding.inflate(layoutInflater)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.Changing_pin))
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val oldPinInput = dialogBinding.etOldPin.text.toString()
                val newPinInput = dialogBinding.etNewPin.text.toString()

                if (oldPinInput == currentPin && newPinInput.length >= 4) {
                    prefs.edit().putString("server_pin", newPinInput).apply()
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
