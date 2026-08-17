package ru.wizand.safeorbit.presentation.server

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.AppInfo
import ru.wizand.safeorbit.data.repository.NotificationRepositoryImpl
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager
import ru.wizand.safeorbit.data.service.NotificationLoggerService
import ru.wizand.safeorbit.data.utils.AllowedAppsPreferences
import ru.wizand.safeorbit.data.utils.AppListUtils
import ru.wizand.safeorbit.databinding.ActivityNotificationSourcesBinding

/**
 * Экран настройки источников уведомлений на сервере.
 * Доступен только после ввода PIN (открывается из настроек сервера).
 */
class NotificationSourcesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSourcesBinding
    private lateinit var adapter: AppsAdapter
    private val repository = NotificationRepositoryImpl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSourcesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppsAdapter(emptyList()) { app, isChecked ->
            togglePackage(app.packageName, isChecked)
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        binding.btnOpenListenerSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnTestNotification.setOnClickListener { sendTestNotification() }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        updateListenerStatus()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppListUtils.getLaunchableApps(this@NotificationSourcesActivity)
            }
            adapter.updateApps(apps)
        }
    }

    private fun togglePackage(packageName: String, isChecked: Boolean) {
        val allowed = AllowedAppsPreferences.getAllowedPackages(this).toMutableSet()
        if (isChecked) allowed.add(packageName) else allowed.remove(packageName)
        AllowedAppsPreferences.saveAllowedPackages(this, allowed)
    }

    private fun updateListenerStatus() {
        val enabled = isNotificationAccessGranted()
        binding.tvListenerStatus.text = if (enabled) {
            getString(R.string.notification_access_granted)
        } else {
            getString(R.string.notification_access_not_granted)
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val component = ComponentName(this, NotificationLoggerService::class.java).flattenToString()
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(enabledListeners) && enabledListeners.contains(component)
    }

    /** Публикует тестовое уведомление напрямую в Firebase. */
    private fun sendTestNotification() {
        val encryptedPrefs = EncryptedPreferencesManager(applicationContext)
        val serverId = encryptedPrefs.getServerId()
        val code = encryptedPrefs.getCode()

        if (serverId.isNullOrBlank() || code.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.server_not_registered), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = repository.publish(
                serverId = serverId,
                code = code,
                packageName = packageName,
                appLabel = getString(R.string.app_name),
                title = getString(R.string.test_notification_title),
                text = getString(R.string.test_notification_text),
                postTime = System.currentTimeMillis()
            )
            result.onSuccess {
                Toast.makeText(this@NotificationSourcesActivity, R.string.test_notification_sent, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@NotificationSourcesActivity, R.string.test_notification_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
