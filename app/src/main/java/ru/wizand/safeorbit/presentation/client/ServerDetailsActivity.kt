package ru.wizand.safeorbit.presentation.client

//import ru.wizand.safeorbit.presentation.client.audio.AudioStreamPlayerServiceLiveKit
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.AnimationDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.ActivityServerDetailsBinding
import ru.wizand.safeorbit.presentation.client.audio.AudioStreamPlayerService
import ru.wizand.safeorbit.presentation.server.ActiveInterval
import ru.wizand.safeorbit.presentation.server.InactivityTimeout
import ru.wizand.safeorbit.utils.NavigationUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServerDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerDetailsBinding
    private val viewModel: ClientViewModel by viewModels()

    private lateinit var serverId: String
    private var lastAudioCode: String? = null
    private var defaultButtonTint: ColorStateList? = null

    private var streamStartTime: Long = 0
    private val streamHandler = Handler(Looper.getMainLooper())
    private val streamTimerRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - streamStartTime
            val seconds = (elapsed / 1000).toInt()
            val minutes = seconds / 60
            val sec = seconds % 60
            binding.textStreamTimer.text = String.format("%02d:%02d", minutes, sec)
            streamHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получение данных
        serverId = intent.getStringExtra("serverId") ?: run {
            Toast.makeText(this, "Ошибка: отсутствует serverId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val name = intent.getStringExtra("name") ?: "Без имени"
        val latitude = intent.getDoubleExtra("lat", 0.0)
        val longitude = intent.getDoubleExtra("lon", 0.0)
        val timestamp = intent.getLongExtra("time", 0L)

        // UI заполнение
        binding.textName.text = name

//        binding.textCoords.text = "Координаты: %.5f, %.5f".format(latitude, longitude)

        val text1 = getString(R.string._5f_5f).format(latitude, longitude)
        binding.textCoords.text = text1

        val text2 = getString(R.string.time_, formatTimestamp(timestamp))
        binding.textTime.text = text2
//        binding.textTime.text = "Время: ${formatTimestamp(timestamp)}"

        binding.textAddress.text = getAddressFromCoords(latitude, longitude)

        defaultButtonTint = binding.buttonListen.backgroundTintList

        viewModel.iconUriMap.observe(this, Observer { map ->
            val iconUri = map[serverId]
            if (!iconUri.isNullOrEmpty()) {
                binding.imageIcon.setImageURI(Uri.parse(iconUri))
            } else {
                binding.imageIcon.setImageResource(R.drawable.ic_marker)
            }
        })

        binding.imageIcon.setOnClickListener {
            val intent = Intent(this, ChangeIconActivity::class.java)
            intent.putExtra("serverId", serverId)
            startActivity(intent)
        }

        binding.buttonRequestLocation.setOnClickListener {
            Log.d("CLIENT_CMD", "Нажимается кнопка R.id.buttonRequestLocation")
            viewModel.requestServerLocationNow(serverId)
            Toast.makeText(this, "Запрошено обновление координат", Toast.LENGTH_SHORT).show()
        }



        binding.buttonChangeIntervals.setOnClickListener {
            showIntervalChangeDialog()
        }

//        binding.buttonNavigate.setOnClickListener {
//            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($name)")
//            val intent = Intent(Intent.ACTION_VIEW, uri)
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//            if (intent.resolveActivity(packageManager) != null) {
//                startActivity(Intent.createChooser(intent, "Выберите приложение для навигации"))
//            } else {
//                Toast.makeText(this, "Нет подходящего приложения для навигации", Toast.LENGTH_SHORT).show()
//            }
//        }

        binding.buttonNavigate.setOnClickListener {
            NavigationUtils.openNavigationChooser(
                this,
                latitude = latitude,
                longitude = longitude,
                name = name
            )
        }

        binding.buttonListen.setOnClickListener {
            if (binding.buttonListen.text == "Остановить") {
                stopAudioStreamUI()
                // Agola
                stopService(Intent(this, AudioStreamPlayerService::class.java))
                //LiveKit
//                stopService(Intent(this, AudioStreamPlayerServiceLiveKit::class.java))
                lastAudioCode?.let { code ->
                    viewModel.getAudioCodeFor(serverId)?.let {
                        viewModel.stopAudioStream(serverId, it)
                    } ?: Log.w("CLIENT_CMD", "⚠️ Нет сохранённого кода для $serverId")
                } ?: Toast.makeText(this, "Неизвестный код трансляции", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("CLIENT_CMD", "Нажимается кнопка R.id.buttonListen")
                viewModel.requestListenMocrofoneNow(serverId) { code ->
                    lastAudioCode = code
                    // Agola
                    ContextCompat.startForegroundService(this, Intent(this, AudioStreamPlayerService::class.java))
                    // LiveKit
//                    ContextCompat.startForegroundService(this, Intent(this, AudioStreamPlayerServiceLiveKit::class.java))
                }
                Toast.makeText(this, "Запрошено прослушивание. Ожидайте", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isAudioStreaming.observe(this) { isStreaming ->
            if (isStreaming) {
                startAudioStreamUI()
            } else {
                stopAudioStreamUI()
                // Agola
                stopService(Intent(this, AudioStreamPlayerService::class.java))
                // LiveKit
//                stopService(Intent(this, AudioStreamPlayerServiceLiveKit::class.java))
            }
        }

        viewModel.toastMessage.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.refreshIcon(serverId)
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun getAddressFromCoords(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Адрес не найден"
        } catch (e: Exception) {
            "Ошибка геокодинга"
        }
    }

    private fun showIntervalChangeDialog() {
        val dialogBinding = ru.wizand.safeorbit.databinding.DialogChangeIntervalsBinding.inflate(layoutInflater)

        val activeOptions = ActiveInterval.entries.toTypedArray()
        val idleOptions = InactivityTimeout.entries.toTypedArray()

//        dialogBinding.spinnerActive.adapter = ArrayAdapter(
//            this,
//            android.R.layout.simple_spinner_item,
//            activeOptions
//        ).apply {
//            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        }
//
//        dialogBinding.spinnerIdle.adapter = ArrayAdapter(
//            this,
//            android.R.layout.simple_spinner_item,
//            idleOptions
//        ).apply {
//            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        }
        val activeAdapter = ArrayAdapter(
            this, // если в Activity — иначе requireContext() в Fragment
            R.layout.dropdown_menu_popup_item,
            activeOptions
        )

        val idleAdapter = ArrayAdapter(
            this,
            R.layout.dropdown_menu_popup_item,
            idleOptions
        )



        dialogBinding.spinnerActive.setAdapter(activeAdapter)
        dialogBinding.spinnerIdle.setAdapter(idleAdapter)




        AlertDialog.Builder(this)
            .setTitle("Настройка интервалов")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val selectedActive = dialogBinding.spinnerActive.text.toString()
                val selectedIdle = dialogBinding.spinnerIdle.text.toString()

                val active = activeOptions.firstOrNull { it.toString() == selectedActive }?.millis ?: activeOptions.first().millis
                val idle = idleOptions.firstOrNull { it.toString() == selectedIdle }?.millis ?: idleOptions.first().millis
                viewModel.sendServerSettings(serverId, active, idle)
                Toast.makeText(this, "Интервалы отправлены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()

        // Принудительное раскрытие выпадающего списка по клику - 2 клика
//        dialogBinding.spinnerActive.setOnClickListener {
//            dialogBinding.spinnerActive.showDropDown()
//        }
//        dialogBinding.spinnerIdle.setOnClickListener {
//            dialogBinding.spinnerIdle.showDropDown()
//        }

        // Принудительное раскрытие выпадающего списка по клику - 1 клик
        dialogBinding.spinnerActive.setOnTouchListener { v, event ->
            v.performClick() // Важно для доступности
            dialogBinding.spinnerActive.showDropDown()
            false
        }
        dialogBinding.spinnerIdle.setOnTouchListener { v, event ->
            v.performClick() // Важно для доступности
            dialogBinding.spinnerIdle.showDropDown()
            false
        }
    }


    private fun startAudioStreamUI() {
        binding.buttonListen.text = "Остановить"
        binding.buttonListen.setTextColor(getColor(R.color.white))
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
        binding.buttonListen.setTextColor(getColor(R.color.white))
        binding.buttonListen.setBackgroundTintList(defaultButtonTint)

        binding.textStreamTimer.visibility = View.GONE
        binding.textAutoOff.visibility = View.GONE
        binding.imageAudioAnim.apply {
            visibility = View.GONE
            (drawable as? AnimationDrawable)?.stop()
        }

        streamHandler.removeCallbacks(streamTimerRunnable)
    }
}
