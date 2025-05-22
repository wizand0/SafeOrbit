package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.yandex.mapkit.geometry.Point
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.DialogMarkerInfoBinding

class MarkerInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogMarkerInfoBinding? = null
    private val binding get() = _binding!!

    private var serverId: String? = null
    private var serverName: String? = null
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var timestamp: Long = 0
    private var iconUri: String? = null

    var onDelete: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            serverId = it.getString(ARG_ID)
            serverName = it.getString(ARG_NAME)
            latitude = it.getDouble(ARG_LAT)
            longitude = it.getDouble(ARG_LON)
            timestamp = it.getLong(ARG_TIME)
            iconUri = it.getString(ARG_ICON)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMarkerInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textServerName.text = serverName ?: "Без имени"
        binding.textCoords.text = "Координаты: %.5f, %.5f".format(latitude, longitude)
        binding.textTime.text = "Время: ${formatTimestamp(timestamp)}"

        if (!iconUri.isNullOrEmpty()) {
            binding.imageIcon.setImageURI(Uri.parse(iconUri))
        } else {
            binding.imageIcon.setImageResource(R.drawable.ic_marker)
        }

        binding.imageIcon.setOnClickListener {
            val intent = Intent(requireContext(), ChangeIconActivity::class.java)
            intent.putExtra("serverId", serverId)
            startActivity(intent)
            dismiss()
        }

        binding.buttonNavigate.setOnClickListener {
            val label = Uri.encode(serverName ?: "Метка")
            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Открыть в навигации"))
            } else {
                Toast.makeText(requireContext(), "Нет подходящего приложения для навигации", Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonDetails.setOnClickListener {
            val intent = Intent(requireContext(), ServerDetailsActivity::class.java).apply {
                putExtra("serverId", serverId)
                putExtra("name", serverName)
                putExtra("lat", latitude)
                putExtra("lon", longitude)
                putExtra("time", timestamp)
                putExtra("icon", iconUri)
            }
            startActivity(intent)
        }

//        binding.buttonListen.setOnClickListener {
//            Toast.makeText(requireContext(), "Функция 'Послушать' пока недоступна", Toast.LENGTH_SHORT).show()
//        }
//
//        binding.buttonDelete.setOnClickListener {
//            AlertDialog.Builder(requireContext())
//                .setTitle("Удалить сервер?")
//                .setMessage("Вы уверены, что хотите удалить $serverName?")
//                .setPositiveButton("Удалить") { _, _ ->
//                    serverId?.let { onDelete?.invoke(it) }
//                    dismiss()
//                }
//                .setNegativeButton("Отмена", null)
//                .show()
//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }

    companion object {
        private const val ARG_ID = "serverId"
        private const val ARG_NAME = "serverName"
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"
        private const val ARG_TIME = "time"
        private const val ARG_ICON = "iconUri"

        fun newInstance(
            serverId: String,
            serverName: String,
            point: Point,
            timestamp: Long,
            iconUri: String?
        ): MarkerInfoBottomSheet {
            val fragment = MarkerInfoBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_ID, serverId)
                putString(ARG_NAME, serverName)
                putDouble(ARG_LAT, point.latitude)
                putDouble(ARG_LON, point.longitude)
                putLong(ARG_TIME, timestamp)
                putString(ARG_ICON, iconUri)
            }
            return fragment
        }
    }
}
