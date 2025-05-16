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

class MarkerInfoBottomSheet(
    private val serverId: String,
    private val serverName: String,
    private val point: Point,
    private val timestamp: Long,
    private val iconUri: String?, // добавлено
    private val onDelete: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogMarkerInfoBinding? = null
    private val binding get() = _binding!!

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

        // Установка текста
        binding.textCoords.text = "Координаты: %.5f, %.5f".format(point.latitude, point.longitude)
        binding.textTime.text = "Время: ${formatTimestamp(timestamp)}"
        binding.textServerName.text = serverName

        // Установка иконки
        if (!iconUri.isNullOrEmpty()) {
            binding.imageIcon.setImageURI(Uri.parse(iconUri))
        } else {
            binding.imageIcon.setImageResource(R.drawable.ic_marker)
        }
        binding.imageIcon.setOnClickListener {
            val intent = Intent(requireContext(), ChangeIconActivity::class.java)
            intent.putExtra("serverId", serverId)
            startActivity(intent)
            dismiss() // закрываем BottomSheet
        }

        // Подробнее
        binding.buttonDetails.setOnClickListener {
            val intent = Intent(requireContext(), ServerDetailsActivity::class.java).apply {
                putExtra("serverId", serverId)
                putExtra("name", serverName)
                putExtra("lat", point.latitude)
                putExtra("lon", point.longitude)
                putExtra("time", timestamp)
                putExtra("icon", iconUri)
            }
            startActivity(intent)
        }

        // Заглушка
        binding.buttonListen.setOnClickListener {
            Toast.makeText(requireContext(), "Функция 'Послушать' пока недоступна", Toast.LENGTH_SHORT).show()
        }

        // Удаление
        binding.buttonDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Удалить сервер?")
                .setMessage("Вы уверены, что хотите удалить $serverName?")
                .setPositiveButton("Удалить") { _, _ ->
                    onDelete(serverId)
                    dismiss()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatTimestamp(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ts))
    }
}
