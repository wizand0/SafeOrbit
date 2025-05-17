package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity

@AndroidEntryPoint
class ServerMainFragment : Fragment() {

    private val viewModel: ServerViewModel by activityViewModels()

    private lateinit var tvServerId: TextView
    private lateinit var tvPairCode: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentCoords: TextView

    private var serviceStarted = false
    private val REQUEST_ACTIVITY_RECOGNITION = 2001

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra("latitude", 0.0) ?: 0.0
            val lon = intent?.getDoubleExtra("longitude", 0.0) ?: 0.0
            val timestamp = intent?.getLongExtra("timestamp", 0L) ?: 0L
            tvCurrentCoords.text = "Координаты: $lat, $lon"
            tvStatus.text = "Время обновления: ${formatTimestamp(timestamp)}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_server_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvServerId = view.findViewById(R.id.tvServerId)
        tvPairCode = view.findViewById(R.id.tvPairCode)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvCurrentCoords = view.findViewById(R.id.tvCurrentCoords)

        val context = requireContext()

        viewModel.serverId.observe(viewLifecycleOwner) { serverId ->
            tvServerId.text = "ID сервера: $serverId"

            if (!serviceStarted) {
                serviceStarted = true
                val activity = requireActivity() as AppCompatActivity

                // ✅ Сначала проверяем разрешение на активность
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        REQUEST_ACTIVITY_RECOGNITION
                    )
                } else {
                    startLocationService(serverId)
                }
            }
        }

        viewModel.code.observe(viewLifecycleOwner) { code ->
            tvPairCode.text = "Код подключения: $code"
        }

        viewModel.audioRequest.observe(viewLifecycleOwner) { request ->
            request?.let {
                Toast.makeText(context, "Запрос на запись от клиента", Toast.LENGTH_SHORT).show()
                AudioRequestHandler(context).handle(it)
            }
        }


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
        if (requestCode == REQUEST_ACTIVITY_RECOGNITION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                viewModel.serverId.value?.let { startLocationService(it) }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Разрешение на распознавание активности не выдано. GPS не будет работать корректно.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("LOCATION_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(locationReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().unregisterReceiver(locationReceiver)
            } else {
                LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationReceiver)
            }
        } catch (e: Exception) {
            Log.w("SERVER_FRAGMENT", "Receiver wasn't registered")
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss, dd MMMM", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
