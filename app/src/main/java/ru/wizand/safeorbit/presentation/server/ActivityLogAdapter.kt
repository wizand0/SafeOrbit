package ru.wizand.safeorbit.presentation.server

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.ActivityLogUiModel
import ru.wizand.safeorbit.databinding.ItemActivityLogBinding

class ActivityLogAdapter : ListAdapter<ActivityLogUiModel, ActivityLogAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemActivityLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: ActivityLogUiModel) {
            if (log.isSummary) {
                binding.tvDate.text = log.date
                binding.tvTime.text = "Итог за день: ${formatNumber(log.dailySteps)} шагов"
                binding.tvTime.setTypeface(null, Typeface.BOLD)
                binding.tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                binding.tvMode.text = ""
                binding.tvSteps.text = ""
                binding.tvDistance.text = ""
                binding.itemRoot.setBackgroundResource(R.drawable.item_background_summary)
            } else {
                binding.tvDate.text = log.date
                binding.tvTime.text = "${log.startHour}:00 - ${log.endHour}:00"
                binding.tvTime.setTypeface(null, Typeface.NORMAL)
                binding.tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                binding.tvMode.text = log.mode
                binding.tvSteps.text = log.steps?.let { "Шагов: $it" } ?: ""
                binding.tvDistance.text = log.distanceMeters?.let { "Расстояние: ${it} м" } ?: ""

                if (log.mode == "Активность") {
                    binding.itemRoot.setBackgroundResource(R.drawable.item_background_active)
                } else {
                    binding.itemRoot.setBackgroundResource(R.drawable.item_background_default)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActivityLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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
