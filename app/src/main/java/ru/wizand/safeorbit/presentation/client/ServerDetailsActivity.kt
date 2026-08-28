package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.databinding.ActivityServerDetailsBinding
import ru.wizand.safeorbit.databinding.DialogChangeIntervalsBinding
import ru.wizand.safeorbit.presentation.client.commands.CommandViewModel
import ru.wizand.safeorbit.presentation.server.ActiveInterval
import ru.wizand.safeorbit.presentation.server.InactivityTimeout
import ru.wizand.safeorbit.utils.NavigationUtils
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ServerDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerDetailsBinding

    // FIX (нулевые координаты): живая подписка на servers/$serverId/location.
    // Ранее экран читал lat/lon только из intent-extra, которые ServerListFragment
    // не передавал → всегда 0.0, и запрос обновления координат на экране не виден.
    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var locationListener: ValueEventListener? = null
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var serverName: String = "Без имени"

    private val clientViewModel: ClientViewModel by viewModels()
    private val commandViewModel: CommandViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()

    private lateinit var serverId: String
    private val notificationsAdapter = NotificationsAdapter(emptyList())

    /** Экспорт CSV через системный диалог сохранения. */
    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) writeCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получение данных
        serverId = intent.getStringExtra("serverId") ?: finishWithError("Нет serverId")
        serverName = intent.getStringExtra("name") ?: "Без имени"
        currentLat = intent.getDoubleExtra("lat", 0.0)
        currentLon = intent.getDoubleExtra("lon", 0.0)
        val timestamp = intent.getLongExtra("time", 0L)

        // Отображение информации
        binding.textName.text = serverName
        renderLocation(currentLat, currentLon, timestamp)

        subscribeToServerLocation()

        // Наблюдение за иконкой
        clientViewModel.iconUriMap.observe(this) { map ->
            val uri = map[serverId]
            if (!uri.isNullOrEmpty()) {
                binding.imageIcon.setImageURI(Uri.parse(uri))
            } else {
                binding.imageIcon.setImageResource(R.drawable.ic_marker)
            }
        }

        // Обновление иконки
        binding.imageIcon.setOnClickListener {
            startActivity(Intent(this, ChangeIconActivity::class.java).apply {
                putExtra("serverId", serverId)
            })
        }

        // Кнопки действий
        binding.buttonRequestLocation.setOnClickListener {
            commandViewModel.requestLocationUpdate(serverId)
            toast(getString(R.string.toast_location_requested))
        }

        binding.buttonChangeIntervals.setOnClickListener {
            showIntervalDialog()
        }

        binding.buttonNavigate.setOnClickListener {
            NavigationUtils.openNavigationChooser(this, currentLat, currentLon, serverName)
        }

        setupNotifications()

        clientViewModel.refreshIcon(serverId)
    }

    /**
     * Подписка на координаты сервера: любые обновления (в т.ч. результат команды
     * request_location_update) сразу отражаются на экране.
     */
    private fun subscribeToServerLocation() {
        unsubscribeFromServerLocation()
        locationListener = firebaseRepository.observeServerLocation(serverId) { location ->
            runOnUiThread {
                currentLat = location.latitude
                currentLon = location.longitude
                renderLocation(location.latitude, location.longitude, location.timestamp)
            }
        }
    }

    private fun unsubscribeFromServerLocation() {
        locationListener?.let {
            firebaseRepository.stopObservingServerLocation(serverId, it)
            locationListener = null
        }
    }

    private fun renderLocation(lat: Double, lon: Double, timestamp: Long) {
        binding.textCoords.text = getString(R.string._5f_5f).format(lat, lon)
        binding.textTime.text = getString(R.string.time_, formatTimestamp(timestamp))
        binding.textAddress.text = getAddressFromCoords(lat, lon)
    }

    override fun onDestroy() {
        unsubscribeFromServerLocation()
        super.onDestroy()
    }

    private fun setupNotifications() {
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = notificationsAdapter

        lifecycleScope.launch {
            notificationViewModel.notifications.collect { items ->
                notificationsAdapter.update(items)
                binding.tvNotificationsEmpty.visibility =
                    if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        binding.buttonExportNotifications.setOnClickListener {
            val fileName = "safeorbit_${serverId}_notifications.csv"
            exportCsvLauncher.launch(fileName)
        }

        binding.buttonClearNotifications.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_notifications_confirm_title))
                .setMessage(getString(R.string.clear_notifications_confirm_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> clearNotifications() }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        notificationViewModel.attach(serverId)
    }

    private fun clearNotifications() {
        lifecycleScope.launch {
            val result = notificationViewModel.clearAll()
            result.onSuccess {
                toast(getString(R.string.notifications_cleared))
            }.onFailure {
                toast(getString(R.string.notifications_clear_failed))
            }
        }
    }

    /** Пишет все видимые уведомления в CSV в формате: время;приложение;заголовок;текст. */
    private fun writeCsv(uri: Uri) {
        val items = notificationViewModel.notifications.value
        if (items.isEmpty()) {
            toast(getString(R.string.notifications_empty))
            return
        }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        // BOM для корректной кодировки в Excel
                        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        out.write("time;app;title;text\n".toByteArray(Charsets.UTF_8))
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        for (n in items) {
                            val line = listOf(
                                sdf.format(Date(n.postTime)),
                                n.appLabel,
                                n.title,
                                n.text
                            ).joinToString(";") { escapeCsv(it) } + "\n"
                            out.write(line.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                toast(getString(R.string.notifications_exported))
            } catch (e: Exception) {
                toast(getString(R.string.notifications_export_failed))
            }
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(';') || value.contains('"') || value.contains('\n')
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun getAddressFromCoords(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.getAddressLine(0)
                ?: getString(R.string.address_not_found)
        } catch (e: Exception) {
            getString(R.string.address_error)
        }
    }

    private fun showIntervalDialog() {
        val dialogBinding = DialogChangeIntervalsBinding.inflate(layoutInflater)

        val activeOptions = ActiveInterval.entries.toTypedArray()
        val idleOptions = InactivityTimeout.entries.toTypedArray()

        dialogBinding.spinnerActive.setAdapter(ArrayAdapter(this, R.layout.dropdown_menu_popup_item, activeOptions))
        dialogBinding.spinnerIdle.setAdapter(ArrayAdapter(this, R.layout.dropdown_menu_popup_item, idleOptions))

        dialogBinding.spinnerActive.setOnTouchListener { v, _ -> v.performClick(); dialogBinding.spinnerActive.showDropDown(); false }
        dialogBinding.spinnerIdle.setOnTouchListener { v, _ -> v.performClick(); dialogBinding.spinnerIdle.showDropDown(); false }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_intervals_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val active = activeOptions.firstOrNull { it.toString() == dialogBinding.spinnerActive.text.toString() }?.millis
                val idle = idleOptions.firstOrNull { it.toString() == dialogBinding.spinnerIdle.text.toString() }?.millis
                if (active != null && idle != null) {
                    commandViewModel.sendServerSettings(serverId, active, idle)
                    toast(getString(R.string.toast_intervals_sent))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun finishWithError(msg: String): Nothing {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
        throw IllegalStateException(msg)
    }
}
