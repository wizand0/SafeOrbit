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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ServerEntity

class ServerListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val viewModel: ClientViewModel by activityViewModels()
    private lateinit var adapter: ServerAdapter
    private var sideInfoContainer: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_server_list, container, false)
        recyclerView = view.findViewById(R.id.serverRecyclerView)
        sideInfoContainer = view.findViewById(R.id.side_info_container) // может быть null в портретной ориентации
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ServerAdapter(
            items = emptyList(),
            onShowInfo = { showDetails(it) },
            onEditName = { showEditDialog(it) },
            onDelete = { deleteServer(it) }
        )
        recyclerView.adapter = adapter

        observeData()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<FloatingActionButton>(R.id.fabAddInList)?.setOnClickListener {
            val existingIds = viewModel.serverNameMap.value?.keys.orEmpty()

            AddServerDialogFragment(existingIds) { serverId, code, nameInput ->
                val name = if (nameInput.isBlank()) serverId else nameInput
                if (existingIds.contains(serverId)) {
                    Toast.makeText(requireContext(), "Сервер с таким ID уже добавлен", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addServer(serverId, code, name)
                    Handler(Looper.getMainLooper()).postDelayed({
                        viewModel.loadAndObserveServers()
                    }, 300)
                }
            }.show(parentFragmentManager, "AddServerDialog")
        }
    }

    private fun observeData() {
        viewModel.serverNameMap.observe(viewLifecycleOwner) { nameMap ->
            val iconMap = viewModel.iconUriMap.value.orEmpty()
            val servers = nameMap.map { (id, name) ->
                ServerEntity(
                    id = 0,
                    serverId = id,
                    name = name,
                    code = "",
                    serverIconUri = iconMap[id]
                )
            }
            adapter.updateData(servers)
        }

        viewModel.iconUriMap.observe(viewLifecycleOwner) {
            viewModel.serverNameMap.value?.let { nameMap ->
                val servers = nameMap.map { (id, name) ->
                    ServerEntity(
                        id = 0,
                        serverId = id,
                        name = name,
                        code = "",
                        serverIconUri = it[id]
                    )
                }
                adapter.updateData(servers)
            }
        }

        viewModel.loadAndObserveServers()
    }

    private fun showEditDialog(server: ServerEntity) {
        val editText = EditText(requireContext())
        editText.setText(server.name)

        AlertDialog.Builder(requireContext())
            .setTitle("Изменить имя")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString()
                val newEntity = server.copy(name = newName)
                viewModel.addServer(newEntity.serverId, newEntity.code, newEntity.name)
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
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDetails(server: ServerEntity) {
        val state = viewModel.mapStates.value?.get(server.serverId)
        val point = state?.latestPoint
        val timestamp = state?.timestamp ?: System.currentTimeMillis()

        if (point != null) {
            if (sideInfoContainer != null) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.side_info_container, MarkerInfoSideFragment.newInstance(
                        serverId = server.serverId,
                        serverName = server.name,
                        point = point,
                        timestamp = timestamp,
                        iconUri = server.serverIconUri
                    ))
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
            Toast.makeText(requireContext(), "Нет координат для сервера", Toast.LENGTH_SHORT).show()
        }
    }
}
