package ru.wizand.safeorbit.presentation.client

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ServerEntity
import ru.wizand.safeorbit.databinding.ItemServerBinding

class ServerAdapter(
    private var items: List<ServerEntity>,
    private val onShowInfo: (ServerEntity) -> Unit,
    private val onEditName: (ServerEntity) -> Unit,
    private val onDelete: (ServerEntity) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    // Публичный геттер для доступа к текущим элементам извне
    val currentItems: List<ServerEntity>
        get() = items

    inner class ServerViewHolder(
        private val binding: ItemServerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ServerEntity) {
            binding.textServerName.text = item.name
            binding.textServerId.text = "ID: ${item.serverId}"

            if (!item.serverIconUri.isNullOrEmpty()) {
                binding.imageServerIcon.setImageURI(Uri.parse(item.serverIconUri))
            } else {
                binding.imageServerIcon.setImageResource(R.drawable.ic_marker)
            }

            binding.root.setOnClickListener { onShowInfo(item) }
            binding.root.setOnLongClickListener {
                onEditName(item)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(binding)
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
