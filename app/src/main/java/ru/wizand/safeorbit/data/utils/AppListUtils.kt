package ru.wizand.safeorbit.data.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import ru.wizand.safeorbit.data.model.AppInfo

/**
 * Список установленных приложений для экрана выбора источников уведомлений.
 */
object AppListUtils {

    /** Приложения с лаунчером, исключая чисто системные. */
    fun getLaunchableApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val allowed = AllowedAppsPreferences.getAllowedPackages(context)

        return installed
            .filter { appInfo ->
                val hasLauncher = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
                val isPureSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                hasLauncher && !isPureSystem
            }
            .mapNotNull { appInfo ->
                try {
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = packageManager.getApplicationIcon(appInfo),
                        isAllowed = allowed.contains(appInfo.packageName)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.appName.lowercase() }
    }

    /** Видимое имя приложения по пакету (для публикации уведомления). */
    fun getAppLabel(context: Context, packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
