package ru.wizand.safeorbit.presentation.server

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.model.AppInfo

class AppsAdapter(
    private var apps: List<AppInfo>,
    private val onToggleChanged: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconImageView: ImageView = view.findViewById(R.id.appIcon)
        val appNameTextView: TextView = view.findViewById(R.id.appName)
        val toggleButton: SwitchMaterial = view.findViewById(R.id.switchToggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_row, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.iconImageView.setImageDrawable(app.icon)
        holder.appNameTextView.text = app.appName

        holder.toggleButton.setOnCheckedChangeListener(null)
        holder.toggleButton.isChecked = app.isAllowed

        holder.toggleButton.setOnCheckedChangeListener { _, isChecked ->
            app.isAllowed = isChecked
            onToggleChanged(app, isChecked)
        }
    }

    fun updateApps(newAppsList: List<AppInfo>) {
        val diffCallback = AppDiffCallback(apps, newAppsList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        apps = newAppsList
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = apps.size
}
