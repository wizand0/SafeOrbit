package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.AnimationDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityServerDetailsBinding
import ru.wizand.safeorbit.databinding.DialogChangeIntervalsBinding
import ru.wizand.safeorbit.presentation.client.audio.AudioStreamPlayerService
import ru.wizand.safeorbit.presentation.client.audio.AudioStreamViewModel
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
    private val audioStreamViewModel: AudioStreamViewModel by viewModels()
    private val commandViewModel: CommandViewModel by viewModels()

    private lateinit var serverId: String
    private var defaultButtonTint: ColorStateList? = null

    private var streamStartTime: Long = 0
    private val streamHandler = Handler(Looper.getMainLooper())
    private val streamTimerRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - streamStartTime
            val minutes = (elapsed / 1000) / 60
            val seconds = (elapsed / 1000) % 60
            binding.textStreamTimer.text = String.format("%02d:%02d", minutes, seconds)
            streamHandler.postDelayed(this, 1000)
        }
    }

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

        defaultButtonTint = binding.buttonListen.backgroundTintList

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

        binding.buttonListen.setOnClickListener {
            if (audioStreamViewModel.isAudioStreaming.value == true) {
                audioStreamViewModel.stopAudioStream(serverId)
            } else {
                audioStreamViewModel.startAudioStream(serverId) { code ->
                    // сохраняем, если нужно
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, AudioStreamPlayerService::class.java)
                    )
                }
                toast("Запрошено прослушивание. Ожидайте")
            }
        }


        // Наблюдение за состоянием аудиопотока
        audioStreamViewModel.isAudioStreaming.observe(this) { active ->
            if (active) startAudioStreamUI() else stopAudioStreamUI()
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

    private fun startAudioStreamUI() {
        binding.buttonListen.text = "Остановить"
        binding.buttonListen.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red))
        binding.textStreamTimer.visibility = View.VISIBLE
        binding.textAutoOff.visibility = View.VISIBLE
        binding.imageAudioAnim.apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.audio_wave_anim)
            (drawable as? AnimationDrawable)?.start()
        }
        streamStartTime = System.currentTimeMillis()
        streamHandler.post(streamTimerRunnable)
    }

    private fun stopAudioStreamUI() {
        binding.buttonListen.text = "Послушать"
        binding.buttonListen.setBackgroundTintList(defaultButtonTint)
        binding.textStreamTimer.visibility = View.GONE
        binding.textAutoOff.visibility = View.GONE
        binding.imageAudioAnim.apply {
            visibility = View.GONE
            (drawable as? AnimationDrawable)?.stop()
        }
        streamHandler.removeCallbacks(streamTimerRunnable)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun finishWithError(msg: String): Nothing {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
        throw IllegalStateException(msg)
    }
}
