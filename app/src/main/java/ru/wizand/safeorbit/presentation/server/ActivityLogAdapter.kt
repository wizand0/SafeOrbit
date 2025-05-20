package ru.wizand.safeorbit.presentation.server

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.ActivityLogUiModel

class ActivityLogAdapter : ListAdapter<ActivityLogUiModel, ActivityLogAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvMode: TextView = view.findViewById(R.id.tvMode)
        val tvSteps: TextView = view.findViewById(R.id.tvSteps)
        val tvDistance: TextView = view.findViewById(R.id.tvDistance)
        val root: LinearLayout = view.findViewById(R.id.itemRoot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = getItem(position)

        if (log.isSummary) {
            holder.tvDate.text = log.date
            holder.tvTime.text = "Итог за день: ${formatNumber(log.dailySteps)} шагов"
            holder.tvTime.setTypeface(null, Typeface.BOLD)
            holder.tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            holder.tvMode.text = ""
            holder.tvSteps.text = ""
            holder.tvDistance.text = ""

            holder.root.setBackgroundResource(R.drawable.item_background_summary) // 🎨 фон для итогов
            return
        }

        holder.tvDate.text = log.date
        holder.tvTime.text = "${log.startHour}:00 - ${log.endHour}:00"
        holder.tvMode.text = log.mode
        holder.tvSteps.text = log.steps?.let { "Шагов: $it" } ?: ""
        holder.tvDistance.text = log.distanceMeters?.let { "Расстояние: ${it} м" } ?: ""

        if (log.mode == "Активность") {
            holder.root.setBackgroundResource(R.drawable.item_background_active)
        } else {
            holder.root.setBackgroundResource(R.drawable.item_background_default)
        }

    }


    class DiffCallback : DiffUtil.ItemCallback<ActivityLogUiModel>() {
        override fun areItemsTheSame(oldItem: ActivityLogUiModel, newItem: ActivityLogUiModel): Boolean {
            return oldItem.date == newItem.date && oldItem.startHour == newItem.startHour
        }

        override fun areContentsTheSame(oldItem: ActivityLogUiModel, newItem: ActivityLogUiModel): Boolean {
            return oldItem == newItem
        }
    }

    private fun formatNumber(number: Int?): String {
        return number?.let { String.format("%,d", it).replace(',', ' ') } ?: "0"
    }

}
