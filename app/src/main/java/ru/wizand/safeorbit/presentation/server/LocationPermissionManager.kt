package ru.wizand.safeorbit.presentation.server

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LocationPermissionManager(
    private val activity: AppCompatActivity,
    private val onPermissionsGranted: () -> Unit // вызывается, когда можно запускать сервис
) {

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST = 1002

    fun checkOrRequestLocationPermissions() {
        if (!hasLocationPermissions()) {
            ActivityCompat.requestPermissions(activity, locationPermissions, LOCATION_PERMISSION_REQUEST)
        } else {
            onPermissionsGranted()
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return locationPermissions.all {
            ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun handlePermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(
                            activity,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        showBackgroundPermissionExplanation()
                    } else {
                        onPermissionsGranted()
                    }
                } else {
                    Toast.makeText(activity, "Разрешения на геолокацию не выданы", Toast.LENGTH_LONG).show()
                }
            }

            BACKGROUND_LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(activity, "Фоновый доступ разрешён", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "Фоновый доступ запрещён — координаты в фоне работать не будут", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showBackgroundPermissionExplanation() {
        AlertDialog.Builder(activity)
            .setTitle("Фоновый доступ к геолокации")
            .setMessage("Разрешите приложению доступ к геолокации в фоновом режиме...")
            .setPositiveButton("Разрешить") { _, _ ->
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_PERMISSION_REQUEST
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Фоновый доступ отключён")
            .setMessage("Чтобы координаты передавались в фоне...")
            .setPositiveButton("Открыть настройки") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", activity.packageName, null)
                activity.startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
