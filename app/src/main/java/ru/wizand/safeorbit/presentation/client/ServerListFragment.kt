package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.ServerEntity

class ServerListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_server_list, container, false)
        recyclerView = view.findViewById(R.id.serverRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        db = AppDatabase.getDatabase(requireContext())
        loadServers()
        return view
    }

    private fun loadServers() {
        lifecycleScope.launch {
            val servers = db.serverDao().getAll()
            recyclerView.adapter = ServerAdapter(
                servers,
                onEdit = { showEditDialog(it) },
                onDelete = { deleteServer(it) }
            )
        }
    }

    private fun showEditDialog(server: ServerEntity) {
        val editText = EditText(requireContext())
        editText.setText(server.name)

        AlertDialog.Builder(requireContext())
            .setTitle("Изменить имя")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString()
                lifecycleScope.launch {
                    db.serverDao().insert(server.copy(name = newName))
                    loadServers()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteServer(server: ServerEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить сервер?")
            .setMessage("Удалить ${server.name}?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    db.serverDao().delete(server)
                    loadServers()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}