package ru.wizand.safeorbit.presentation.client

import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import ru.wizand.safeorbit.databinding.FragmentMarkerInfoSideBinding
import com.yandex.mapkit.geometry.Point
import java.text.SimpleDateFormat
import java.util.*

class MarkerInfoSideFragment : Fragment() {

    private var _binding: FragmentMarkerInfoSideBinding? = null
    private val binding get() = _binding!!

    private lateinit var serverId: String
    private lateinit var serverName: String
    private lateinit var point: Point
    private var timestamp: Long = 0
    private var iconUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            serverId = it.getString("serverId") ?: ""
            serverName = it.getString("serverName") ?: ""
            point = Point(it.getDouble("lat"), it.getDouble("lon"))
            timestamp = it.getLong("timestamp")
            iconUri = it.getString("icon")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMarkerInfoSideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.textCoords.text = "Координаты: %.5f, %.5f".format(point.latitude, point.longitude)
        binding.textTime.text = "Время: ${formatTimestamp(timestamp)}"
        binding.textServerName.text = serverName

        if (!iconUri.isNullOrEmpty()) {
            binding.imageIcon.setImageURI(Uri.parse(iconUri))
        }

        binding.buttonDetails.setOnClickListener {
            val intent = android.content.Intent(requireContext(), ServerDetailsActivity::class.java).apply {
                putExtra("serverId", serverId)
                putExtra("name", serverName)
                putExtra("lat", point.latitude)
                putExtra("lon", point.longitude)
                putExtra("time", timestamp)
                putExtra("icon", iconUri)
            }
            startActivity(intent)
        }
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(serverId: String, serverName: String, point: Point, timestamp: Long, iconUri: String?) =
            MarkerInfoSideFragment().apply {
                arguments = Bundle().apply {
                    putString("serverId", serverId)
                    putString("serverName", serverName)
                    putDouble("lat", point.latitude)
                    putDouble("lon", point.longitude)
                    putLong("timestamp", timestamp)
                    putString("icon", iconUri)
                }
            }
    }
}
