package ru.wizand.safeorbit.presentation.server

import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.FragmentServerMainBinding
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

@AndroidEntryPoint
class ServerMainFragment : Fragment() {

    private var _binding: FragmentServerMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ServerViewModel by activityViewModels()
    private lateinit var prefs: SharedPreferences
    private var serviceStarted = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "active_interval" -> {
                val value = prefs.getLong(key, -1)
                if (value > 0) {
                    val label = ActiveInterval.fromMillis(value).label
                    val text = getString(R.string.interval_active) + label
                    binding.tvActiveInterval.text = text
                }
            }

            "inactivity_timeout" -> {
                val value = prefs.getLong(key, -1)
                if (value > 0) {
                    val label = InactivityTimeout.fromMillis(value).label
                    val text = getString(R.string.interval_econom) + label
                    binding.tvInterval.text = text
                }
            }
        }
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("SERVER_FRAGMENT", "Получен broadcast LOCATION_UPDATE")

            val lat = intent?.getDoubleExtra("latitude", 0.0)
            val lon = intent?.getDoubleExtra("longitude", 0.0)
            val timestamp = intent?.getLongExtra("timestamp", 0L)
            val mode = intent?.getStringExtra("mode") ?: "..."

            if (lat != null && lon != null && timestamp != null) {
                viewModel.updateLastLocation(lat, lon, timestamp)
            }

            viewModel.updateMode(mode)

            intent?.getLongExtra("active_interval", -1L)?.takeIf { it > 0 }?.let {
                val label = ActiveInterval.fromMillis(it).label
                val text = getString(R.string.interval_active) + label
                binding.tvActiveInterval.text = text
            }

            intent?.getLongExtra("inactivity_timeout", -1L)?.takeIf { it > 0 }?.let {
                val label = InactivityTimeout.fromMillis(it).label
                val text = getString(R.string.interval_econom) + label
                binding.tvInterval.text = text
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentServerMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        prefs.getLong("active_interval", -1).takeIf { it > 0 }?.let {
            val text = getString(R.string.interval_active) + ActiveInterval.fromMillis(it).label
            binding.tvActiveInterval.text = text
        }

        prefs.getLong("inactivity_timeout", -1).takeIf { it > 0 }?.let {
            val text = getString(R.string.interval_econom) + InactivityTimeout.fromMillis(it).label
            binding.tvInterval.text = text
        }

        viewModel.serverId.observe(viewLifecycleOwner) { serverId ->
            val text = getString(R.string.server_id) + serverId
            binding.tvServerId.text = text
            if (!serviceStarted && serverId != null) {
                serviceStarted = true
                startLocationService(serverId)
            }
            viewModel.code.value?.let { code ->
                if (serverId != null) {
                    generateQrCode(serverId, code)
                }
            }

        }

        viewModel.code.observe(viewLifecycleOwner) { code ->
            val text = getString(R.string.code) + code
            binding.tvPairCode.text = text
            viewModel.serverId.value?.let { serverId ->
                if (code != null) {
                    generateQrCode(serverId, code)
                }
            }
        }

        viewModel.audioRequest.observe(viewLifecycleOwner) { request ->
            request?.let {
                Toast.makeText(requireContext(), "Запрос на запись от клиента", Toast.LENGTH_SHORT).show()
                AudioRequestHandler(requireContext()).handle(it)
            }
        }

        viewModel.lastKnownLatLon.observe(viewLifecycleOwner) { (lat, lon) ->
            val text = getString(R.string.coordinates) + lat + ", " + lon
            binding.tvCurrentCoords.text = text
        }

        viewModel.lastUpdateTimestamp.observe(viewLifecycleOwner) { timestamp ->
            val text = getString(R.string.update_time) + formatTimestamp(timestamp)
            binding.tvStatus.text = text
        }

        viewModel.mode.observe(viewLifecycleOwner) { mode ->
            val text = getString(R.string.work_mode) + mode
            binding.tvMode.text = text
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(locationReceiver, IntentFilter("LOCATION_UPDATE"))
    }

    override fun onPause() {
        super.onPause()
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationReceiver)
        } catch (e: Exception) {
            Log.w("SERVER_FRAGMENT", "Receiver wasn't registered: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        _binding = null
    }

    private fun startLocationService(serverId: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val role = prefs.getString("user_role", null)

        if (role != "server") {
            Log.w("SERVER_FRAGMENT", "⛔ Попытка запустить LocationService при роли: $role")
            return
        }

        val intent = Intent(requireContext(), LocationService::class.java).apply {
            putExtra("server_id", serverId)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }


    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss, dd MMMM", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun generateQrCode(serverId: String, code: String) {
        val data = "$serverId|$code"
        val writer = com.google.zxing.MultiFormatWriter()
        val matrix = writer.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
        val bitmap = com.journeyapps.barcodescanner.BarcodeEncoder().createBitmap(matrix)
        binding.ivQrCode.setImageBitmap(bitmap)
    }

}
