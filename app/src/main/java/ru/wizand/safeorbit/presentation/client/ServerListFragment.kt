package ru.wizand.safeorbit.presentation.client

import ServerAdapter
import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ServerEntity

class ServerListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val viewModel: ClientViewModel by activityViewModels()
    private lateinit var adapter: ServerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_server_list, container, false)
        recyclerView = view.findViewById(R.id.serverRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ServerAdapter(
            items = emptyList(),
            onEdit = { showEditDialog(it) },
            onDelete = { deleteServer(it) }
        )
        recyclerView.adapter = adapter

        observeData()

        return view
    }

    private fun observeData() {
        viewModel.serverNameMap.observe(viewLifecycleOwner) { nameMap ->
            val iconMap = viewModel.iconUriMap.value.orEmpty()
            val servers = nameMap.map { (id, name) ->
                ServerEntity(
                    id = 0, // неважно для отображения
                    serverId = id,
                    name = name,
                    code = "", // код тоже не нужен для UI
                    serverIconUri = iconMap[id]
                )
            }
            adapter.updateData(servers)
        }

        viewModel.iconUriMap.observe(viewLifecycleOwner) {
            // Просто вызов повторной сборки списка
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

        // Инициируем загрузку при старте
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
}
