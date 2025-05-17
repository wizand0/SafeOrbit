package ru.wizand.safeorbit.presentation.client

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import ru.wizand.safeorbit.R
import java.text.SimpleDateFormat
import java.util.*


class ServerDetailsActivity : AppCompatActivity() {

    private val viewModel: ClientViewModel by viewModels()
    private lateinit var imageIcon: ImageView
    private lateinit var serverId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_details)

        // Получаем данные из Intent
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

        // Подписываемся на LiveData для иконки
        viewModel.iconUriMap.observe(this, Observer { map ->
            val iconUri = map[serverId]
            if (!iconUri.isNullOrEmpty()) {
                imageIcon.setImageURI(Uri.parse(iconUri))
            } else {
                imageIcon.setImageResource(R.drawable.ic_marker)
            }
        })

        // Клик на иконку -> смена
        imageIcon.setOnClickListener {
            val intent = Intent(this, ChangeIconActivity::class.java)
            intent.putExtra("serverId", serverId)
            startActivity(intent)
        }

        // Текстовые данные
        findViewById<TextView>(R.id.textName).text = name
        findViewById<TextView>(R.id.textCoords).text = "Координаты: %.5f, %.5f".format(latitude, longitude)
        findViewById<TextView>(R.id.textTime).text = "Время: ${formatTimestamp(timestamp)}"
        findViewById<TextView>(R.id.textAddress).text = getAddressFromCoords(latitude, longitude)

        // Кнопка навигации
        findViewById<Button>(R.id.buttonNavigate).setOnClickListener {
            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($name)")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

// Покажет список подходящих навигационных приложений
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Выберите приложение для навигации"))
            } else {
                Toast.makeText(this, "Нет подходящего приложения для навигации", Toast.LENGTH_SHORT).show()
            }
        }

        // Запрашиваем обновление иконки вручную (на случай первого запуска)
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
}
