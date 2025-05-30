package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ru.wizand.safeorbit.data.ServerEntity
import ru.wizand.safeorbit.databinding.FragmentServerListBinding

class ServerListFragment : Fragment() {

    private var _binding: FragmentServerListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClientViewModel by activityViewModels()
    private lateinit var adapter: ServerAdapter

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val parts = result.contents.split("|")
            if (parts.size == 2) {
                val serverId = parts[0]
                val code = parts[1]
                tryAddServer(serverId, code, serverId)
            } else {
                Toast.makeText(requireContext(), "Некорректный QR-код", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServerListBinding.inflate(inflater, container, false)

        binding.serverRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ServerAdapter(
            items = emptyList(),
            onShowInfo = { showDetails(it) },
            onEditName = { showEditDialog(it) },
            onDelete = { deleteServer(it) }
        )
        binding.serverRecyclerView.adapter = adapter

        // Добавим поддержку свайпа
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val server = adapter.currentItems[vh.adapterPosition]
                deleteServer(server)
            }
        }).attachToRecyclerView(binding.serverRecyclerView)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabAddInList.setOnClickListener {
            val existingIds = viewModel.serverNameMap.value?.keys.orEmpty()
            AddServerDialogFragment(existingIds) { serverId, code, nameInput ->
                val name = if (nameInput.isBlank()) serverId else nameInput
                tryAddServer(serverId, code, name)
            }.show(parentFragmentManager, "AddServerDialog")
        }

        binding.fabScanQr?.setOnClickListener {
            val options = ScanOptions().apply {
                setPrompt("Отсканируйте QR с ID и кодом")
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
            qrScannerLauncher.launch(options)
        }

        observeData()
    }

    private fun tryAddServer(serverId: String, code: String, name: String) {
        val existingIds = viewModel.serverNameMap.value?.keys.orEmpty()
        if (existingIds.contains(serverId)) {
            Toast.makeText(requireContext(), "Сервер уже добавлен", Toast.LENGTH_SHORT).show()
            return
        }

        val ref = FirebaseDatabase.getInstance()
            .getReference("servers")
            .child(serverId)
            .child("code")

        ref.get().addOnSuccessListener {
            val actualCode = it.getValue(String::class.java)
            if (actualCode == code) {
                viewModel.addServer(serverId, code, name)
                Handler(Looper.getMainLooper()).postDelayed({
                    viewModel.loadAndObserveServers()
                }, 300)
                Toast.makeText(requireContext(), "Сервер $serverId добавлен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Неверный ID или код", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Ошибка подключения к Firebase", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        viewModel.serverNameMap.observe(viewLifecycleOwner) { nameMap ->
            val iconMap = viewModel.iconUriMap.value.orEmpty()
            val servers = nameMap.map { (id, name) ->
                ServerEntity(id = 0, serverId = id, name = name, code = "", serverIconUri = iconMap[id])
            }
            adapter.updateData(servers)
        }

        viewModel.iconUriMap.observe(viewLifecycleOwner) { iconMap ->
            val nameMap = viewModel.serverNameMap.value.orEmpty()
            val servers = nameMap.map { (id, name) ->
                ServerEntity(id = 0, serverId = id, name = name, code = "", serverIconUri = iconMap[id])
            }
            adapter.updateData(servers)
        }

        viewModel.loadAndObserveServers()
    }

    private fun showEditDialog(server: ServerEntity) {
        val editText = EditText(requireContext()).apply {
            setText(server.name)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Изменить имя")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString()
                viewModel.addServer(server.serverId, server.code, newName)
                viewModel.loadAndObserveServers()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteServer(server: ServerEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить сервер?")
            .setMessage("Удалить ${server.name}?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteServer(server.serverId)
            }
            .setNegativeButton("Отмена") { _, _ ->
                adapter.notifyDataSetChanged() // Вернуть элемент после отмены
            }
            .show()
    }

    private fun showDetails(server: ServerEntity) {
        val state = viewModel.mapStates.value?.get(server.serverId)
        val point = state?.latestPoint
        val timestamp = state?.timestamp ?: System.currentTimeMillis()

        if (point != null) {
            if (binding.sideInfoContainer != null) {
                parentFragmentManager.beginTransaction()
                    .replace(
                        binding.sideInfoContainer!!.id,
                        MarkerInfoSideFragment.newInstance(
                            serverId = server.serverId,
                            serverName = server.name,
                            point = point,
                            timestamp = timestamp,
                            iconUri = server.serverIconUri
                        )
                    )
                    .commit()
            } else {
                MarkerInfoBottomSheet.newInstance(
                    serverId = server.serverId,
                    serverName = server.name,
                    point = point,
                    timestamp = timestamp,
                    iconUri = server.serverIconUri
                ).apply {
                    onDelete = { viewModel.deleteServer(it) }
                }.show(parentFragmentManager, "info")
            }
        } else {

            AlertDialog.Builder(requireContext())
                .setTitle("Нет координат")
                .setMessage("Отправить запрос координат серверу?")
                .setPositiveButton("Да") { _, _ ->
                    viewModel.requestServerLocationNow(server.serverId)
                }
                .setNegativeButton("Отмена", null)
                .show()

            Toast.makeText(requireContext(), "Нет координат для сервера", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
