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
    private val onEdit: (ServerEntity) -> Unit,
    private val onDelete: (ServerEntity) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    inner class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textServerName)
        val serverId: TextView = view.findViewById(R.id.textServerId)
        val icon: ImageView = view.findViewById(R.id.imageServerIcon)

        init {
            view.setOnClickListener {
                onEdit(items[bindingAdapterPosition])
            }
            view.setOnLongClickListener {
                onDelete(items[bindingAdapterPosition])
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

        if (!item.serverIconUri.isNullOrEmpty()) {
            holder.icon.setImageURI(Uri.parse(item.serverIconUri))
        } else {
            holder.icon.setImageResource(R.drawable.ic_marker)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<ServerEntity>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].serverId == newList[newItemPosition].serverId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newList[newItemPosition]
            }
        })

        items = newList
        diff.dispatchUpdatesTo(this)
    }
}
