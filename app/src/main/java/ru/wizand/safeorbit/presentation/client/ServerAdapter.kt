package ru.wizand.safeorbit.presentation.client

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ServerEntity

class ServerAdapter(
    private var items: List<ServerEntity>,
    private val onShowInfo: (ServerEntity) -> Unit,
    private val onEditName: (ServerEntity) -> Unit,
    private val onDelete: (ServerEntity) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.textServerName)
        private val serverId: TextView = view.findViewById(R.id.textServerId)
        private val icon: ImageView = view.findViewById(R.id.imageServerIcon)

        fun bind(item: ServerEntity) {
            name.text = item.name
            serverId.text = "ID: ${item.serverId}"
            if (!item.serverIconUri.isNullOrEmpty()) {
                icon.setImageURI(Uri.parse(item.serverIconUri))
            } else {
                icon.setImageResource(R.drawable.ic_marker)
            }

            itemView.setOnClickListener { onShowInfo(item) }
            itemView.setOnLongClickListener {
                onEditName(item)
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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<ServerEntity>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                items[oldItemPosition].serverId == newList[newItemPosition].serverId

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                items[oldItemPosition] == newList[newItemPosition]
        })

        items = newList
        diff.dispatchUpdatesTo(this)
    }
}
