package ru.wizand.safeorbit.presentation.server

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.ActivityLogEntity
import ru.wizand.safeorbit.data.model.ActivityLogUiModel

class ActivityLogAdapter(private val logs: List<ActivityLogUiModel>) :
    RecyclerView.Adapter<ActivityLogAdapter.ViewHolder>() {



//class ActivityLogAdapter(private val logs: List<ActivityLogEntity>) :
//    RecyclerView.Adapter<ActivityLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val tvTime = view.findViewById<TextView>(R.id.tvTime)
        val tvMode = view.findViewById<TextView>(R.id.tvMode)
        val tvSteps = view.findViewById<TextView>(R.id.tvSteps)
        val tvDistance = view.findViewById<TextView>(R.id.tvDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = logs.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]

        holder.tvDate.text = log.date
        holder.tvTime.text = "${log.startHour}:00 - ${log.endHour}:00"
        holder.tvMode.text = log.mode
        holder.tvSteps.text = log.steps?.let { "Шагов: $it" } ?: ""
        holder.tvDistance.text = log.distanceMeters?.let { "Расстояние: ${it} м" } ?: ""

        val root = holder.itemView.findViewById<LinearLayout>(R.id.itemRoot)
        if (log.mode == "Активность") {
            root.setBackgroundResource(R.drawable.item_background_active)
        } else {
            root.setBackgroundResource(R.drawable.item_background_default)
        }
    }
}
