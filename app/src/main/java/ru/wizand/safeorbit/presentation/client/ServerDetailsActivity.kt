package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.server.ActiveInterval
import ru.wizand.safeorbit.presentation.server.InactivityTimeout
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat

class ServerDetailsActivity : AppCompatActivity() {

    private val viewModel: ClientViewModel by viewModels()
    private lateinit var imageIcon: ImageView
    private lateinit var serverId: String

    private var lastAudioCode: String? = null

    private lateinit var buttonListen: Button
    private lateinit var textStreamTimer: TextView
    private lateinit var imageAudioAnim: ImageView

    private var streamStartTime: Long = 0
    private val streamHandler = Handler(Looper.getMainLooper())
    private val streamTimerRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - streamStartTime
            val seconds = (elapsed / 1000).toInt()
            val minutes = seconds / 60
            val sec = seconds % 60
            textStreamTimer.text = String.format("%02d:%02d", minutes, sec)
            streamHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_details)



        serverId = intent.getStringExtra("serverId") ?: run {
            Toast.makeText(this, "Ошибка: отсутствует serverId", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val name = intent.getStringExtra("name") ?: "Без имени"
        val latitude = intent.getDoubleExtra("lat", 0.0)
        val longitude = intent.getDoubleExtra("lon", 0.0)
        val timestamp = intent.getLongExtra("time", 0L)

        imageIcon = findViewById(R.id.imageIcon)

        viewModel.iconUriMap.observe(this, Observer { map ->
            val iconUri = map[serverId]
            if (!iconUri.isNullOrEmpty()) {
                imageIcon.setImageURI(Uri.parse(iconUri))
            } else {
                imageIcon.setImageResource(R.drawable.ic_marker)
            }
        })

        imageIcon.setOnClickListener {
            val intent = Intent(this, ChangeIconActivity::class.java)
            intent.putExtra("serverId", serverId)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.textName).text = name
        findViewById<TextView>(R.id.textCoords).text = "Координаты: %.5f, %.5f".format(latitude, longitude)
        findViewById<TextView>(R.id.textTime).text = "Время: ${formatTimestamp(timestamp)}"
        findViewById<TextView>(R.id.textAddress).text = getAddressFromCoords(latitude, longitude)

        buttonListen = findViewById(R.id.buttonListen)
        textStreamTimer = findViewById(R.id.textStreamTimer)
        imageAudioAnim = findViewById(R.id.imageAudioAnim)

        findViewById<Button>(R.id.buttonRequestLocation).setOnClickListener {
            Log.d("CLIENT_CMD", "Нажимается кнопка R.id.buttonRequestLocation")
            viewModel.requestServerLocationNow(serverId)
            Toast.makeText(this, "Запрошено обновление координат", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.buttonChangeIntervals).setOnClickListener {
            showIntervalChangeDialog()
        }

        findViewById<Button>(R.id.buttonNavigate).setOnClickListener {
            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($name)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Выберите приложение для навигации"))
            } else {
                Toast.makeText(this, "Нет подходящего приложения для навигации", Toast.LENGTH_SHORT).show()
            }
        }

        buttonListen.setOnClickListener {
            if (buttonListen.text == "Остановить") {
                stopAudioStreamUI()
                lastAudioCode?.let { code ->
//                    viewModel.stopAudioStream(serverId, code)
                    viewModel.getAudioCodeFor(serverId)?.let { code ->
                        viewModel.stopAudioStream(serverId, code)
                    } ?: Log.w("CLIENT_CMD", "⚠️ Нет сохранённого кода для $serverId")
                } ?: Toast.makeText(this, "Неизвестный код трансляции", Toast.LENGTH_SHORT).show()
            } else {
                Log.d("CLIENT_CMD", "Нажимается кнопка R.id.buttonListen")
                viewModel.requestListenMocrofoneNow(serverId) { code ->
                    lastAudioCode = code
                    runOnUiThread { startAudioStreamUI() }
                }
                Toast.makeText(this, "Запрошено прослушивание. Ожидайте", Toast.LENGTH_SHORT).show()
            }
        }


        viewModel.toastMessage.observe(this) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.buttonDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удалить сервер?")
                .setMessage("Вы уверены, что хотите удалить $name?")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteServer(serverId)
                    Toast.makeText(this, "Сервер удалён", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("Отмена", null)
                .show()
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_intervals, null)

        val spinnerActive = dialogView.findViewById<Spinner>(R.id.spinnerActive)
        val spinnerIdle = dialogView.findViewById<Spinner>(R.id.spinnerIdle)

        val activeOptions = ActiveInterval.values()
        val idleOptions = InactivityTimeout.values()

        spinnerActive.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            activeOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerIdle.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            idleOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(this)
            .setTitle("Настройка интервалов")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val active = activeOptions[spinnerActive.selectedItemPosition].millis
                val idle = idleOptions[spinnerIdle.selectedItemPosition].millis
                Log.d("CLIENT_CMD", "📤 Отправляем интервалы: active=$active, idle=$idle для $serverId")
                viewModel.sendServerSettings(serverId, active, idle)
                Toast.makeText(this, "Интервалы отправлены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startAudioStreamUI() {
        buttonListen.text = "Остановить"
        buttonListen.setTextColor(getColor(R.color.white))
        buttonListen.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red))

        textStreamTimer.visibility = View.VISIBLE
        imageAudioAnim.visibility = View.VISIBLE
        imageAudioAnim.setImageResource(R.drawable.audio_wave_anim)
        (imageAudioAnim.drawable as? AnimationDrawable)?.start()

        streamStartTime = System.currentTimeMillis()
        streamHandler.post(streamTimerRunnable)
    }

    private fun stopAudioStreamUI() {
        buttonListen.text = "Послушать"
        buttonListen.setTextColor(getColor(R.color.white))
        buttonListen.setBackgroundTintList(null) // сбросить цвет на дефолтный Material

        textStreamTimer.visibility = View.GONE
        imageAudioAnim.visibility = View.GONE
        (imageAudioAnim.drawable as? AnimationDrawable)?.stop()
        streamHandler.removeCallbacks(streamTimerRunnable)
    }


}
