package ru.wizand.safeorbit.device

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import ru.wizand.safeorbit.R

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, context.getString(R.string.toast_app_is_admin), Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context,
            context.getString(R.string.toast_app_is_not_admin), Toast.LENGTH_SHORT).show()
    }
}