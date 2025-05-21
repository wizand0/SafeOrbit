
package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.server.ActiveInterval

@AndroidEntryPoint
class ServerMainFragment : Fragment() {

    private val viewModel: ServerViewModel by activityViewModels()

    private lateinit var tvServerId: TextView
    private lateinit var tvPairCode: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentCoords: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvInterval: TextView
    private lateinit var tvActiveInterval: TextView

    private var serviceStarted = false
    private val LOCATION_PERMISSION_REQUEST = 2002

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("SERVER_FRAGMENT", "Получен broadcast LOCATION_UPDATE")

            val hasLatLon = intent?.hasExtra("latitude") == true && intent.hasExtra("longitude")
            val hasTimestamp = intent?.hasExtra("timestamp") == true
            val mode = intent?.getStringExtra("mode") ?: "..."

            if (hasLatLon && hasTimestamp) {
                val lat = intent?.getDoubleExtra("latitude", 0.0)
                val lon = intent?.getDoubleExtra("longitude", 0.0)
                val timestamp = intent?.getLongExtra("timestamp", 0L)

                if (lat != null && lon != null && timestamp != null) {
                    viewModel.updateLastLocation(lat, lon, timestamp)
                }
            }

            // Обновление режима
            viewModel.updateMode(mode)

            // Обновление интервалов из broadcast
            val activeInterval = intent?.getLongExtra("active_interval", -1L) ?: -1L
            val idleInterval = intent?.getLongExtra("inactivity_timeout", -1L) ?: -1L

            if (activeInterval > 0) {
                val label = ActiveInterval.fromMillis(activeInterval).label
                tvActiveInterval.text = "Интервал АКТИВНЫЙ: $label"
            }

            if (idleInterval > 0) {
                val label = InactivityTimeout.fromMillis(idleInterval).label
                tvInterval.text = "Интервал ЭКОНОМ: $label"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_server_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val introShown = prefs.getBoolean("permissions_intro_shown", false)

        if (!introShown) {
            showPermissionsIntroDialog {
                prefs.edit().putBoolean("permissions_intro_shown", true).apply()
                checkAndStartLocationService(viewModel.serverId.value ?: return@showPermissionsIntroDialog)
            }
        } else {
            checkAndStartLocationService(viewModel.serverId.value ?: return)
        }

        tvServerId = view.findViewById(R.id.tvServerId)
        tvPairCode = view.findViewById(R.id.tvPairCode)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvCurrentCoords = view.findViewById(R.id.tvCurrentCoords)
        tvMode = view.findViewById(R.id.tvMode)
        tvInterval = view.findViewById(R.id.tvInterval)
        tvActiveInterval = view.findViewById(R.id.tvActiveInterval)

        viewModel.serverId.observe(viewLifecycleOwner) { serverId ->
            tvServerId.text = "ID сервера: $serverId"
            if (!serviceStarted) {
                serviceStarted = true
                if (serverId != null) checkAndStartLocationService(serverId)
            }
        }

        viewModel.code.observe(viewLifecycleOwner) { code ->
            tvPairCode.text = "Код подключения: $code"
        }

        viewModel.audioRequest.observe(viewLifecycleOwner) { request ->
            request?.let {
                Toast.makeText(requireContext(), "Запрос на запись от клиента", Toast.LENGTH_SHORT).show()
                AudioRequestHandler(requireContext()).handle(it)
            }
        }

        viewModel.lastKnownLatLon.observe(viewLifecycleOwner) { (lat, lon) ->
            tvCurrentCoords.text = "Координаты: $lat, $lon"
        }

        viewModel.lastUpdateTimestamp.observe(viewLifecycleOwner) { timestamp ->
            tvStatus.text = "Время обновления: ${formatTimestamp(timestamp)}"
        }

        viewModel.mode.observe(viewLifecycleOwner) { mode ->
            tvMode.text = "Режим работы: $mode"
        }
    }

    private fun checkAndStartLocationService(serverId: String) {
        val context = requireContext()
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_REQUEST)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundPermissionDialog()
            } else {
                startLocationService(serverId)
            }
        }
    }

    private fun showBackgroundPermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Фоновый доступ к геолокации")
            .setMessage("Приложению необходимо разрешение на доступ к местоположению в фоновом режиме, чтобы отслеживать координаты, даже когда вы не используете приложение.")
            .setPositiveButton("Разрешить") { _, _ ->
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), LOCATION_PERMISSION_REQUEST)
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    private fun startLocationService(serverId: String) {
        val intent = Intent(requireContext(), LocationService::class.java).apply {
            putExtra("server_id", serverId)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkAndStartLocationService(viewModel.serverId.value ?: return)
            } else {
                Toast.makeText(requireContext(), "Не все разрешения выданы. Геолокация не будет работать.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("LOCATION_UPDATE")
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(locationReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationReceiver)
        } catch (e: Exception) {
            Log.w("SERVER_FRAGMENT", "Receiver wasn't registered: ${e.message}")
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss, dd MMMM", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun showPermissionsIntroDialog(onContinue: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Зачем нужны разрешения")
            .setMessage(
                """
            Для корректной работы в роли сервера приложению нужны следующие разрешения:

            • Геолокация — для определения текущего положения.
            • Фоновый доступ — чтобы работать даже с выключенным экраном.
            • Распознавание активности — чтобы отслеживать движение и экономить заряд.
            • Запуск служб — для фоновой работы.
            
            Без них приложение не сможет отправлять координаты клиенту.
            """.trimIndent()
            )
            .setPositiveButton("Продолжить") { _, _ -> onContinue() }
            .setCancelable(false)
            .show()
    }
}
