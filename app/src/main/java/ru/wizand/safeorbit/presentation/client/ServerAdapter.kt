package ru.wizand.safeorbit.presentation.client

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ServerEntity

class ServerAdapter(
    private val items: List<ServerEntity>,
    private val onEdit: (ServerEntity) -> Unit,
    private val onDelete: (ServerEntity) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<TextView>(R.id.textServerName)
        val serverId = view.findViewById<TextView>(R.id.textServerId)

        init {
            view.setOnClickListener {
                onEdit(items[adapterPosition])
            }
            view.setOnLongClickListener {
                onDelete(items[adapterPosition])
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.serverId.text = "ID: ${item.serverId}"
    }

    override fun getItemCount(): Int = items.size
}