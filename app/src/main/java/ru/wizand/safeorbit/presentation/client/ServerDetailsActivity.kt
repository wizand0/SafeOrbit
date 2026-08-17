package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityServerDetailsBinding
import ru.wizand.safeorbit.databinding.DialogChangeIntervalsBinding
import ru.wizand.safeorbit.presentation.client.commands.CommandViewModel
import ru.wizand.safeorbit.presentation.server.ActiveInterval
import ru.wizand.safeorbit.presentation.server.InactivityTimeout
import ru.wizand.safeorbit.utils.NavigationUtils
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class ServerDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerDetailsBinding

    private val clientViewModel: ClientViewModel by viewModels()
    private val commandViewModel: CommandViewModel by viewModels()

    private lateinit var serverId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получение данных
        serverId = intent.getStringExtra("serverId") ?: finishWithError("Нет serverId")
        val name = intent.getStringExtra("name") ?: "Без имени"
        val lat = intent.getDoubleExtra("lat", 0.0)
        val lon = intent.getDoubleExtra("lon", 0.0)
        val timestamp = intent.getLongExtra("time", 0L)

        // Отображение информации
        binding.textName.text = name
        binding.textCoords.text = getString(R.string._5f_5f).format(lat, lon)
        binding.textTime.text = getString(R.string.time_, formatTimestamp(timestamp))
        binding.textAddress.text = getAddressFromCoords(lat, lon)

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
            toast("Запрошено обновление координат")
        }

        binding.buttonChangeIntervals.setOnClickListener {
            showIntervalDialog()
        }

        binding.buttonNavigate.setOnClickListener {
            NavigationUtils.openNavigationChooser(this, lat, lon, name)
        }

        clientViewModel.refreshIcon(serverId)
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun getAddressFromCoords(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.getAddressLine(0)
                ?: "Адрес не найден"
        } catch (e: Exception) {
            "Ошибка геокодинга"
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
            .setTitle("Настройка интервалов")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val active = activeOptions.firstOrNull { it.toString() == dialogBinding.spinnerActive.text.toString() }?.millis
                val idle = idleOptions.firstOrNull { it.toString() == dialogBinding.spinnerIdle.text.toString() }?.millis
                if (active != null && idle != null) {
                    commandViewModel.sendServerSettings(serverId, active, idle)
                    toast("Интервалы отправлены")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun finishWithError(msg: String): Nothing {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
        throw IllegalStateException(msg)
    }
}
