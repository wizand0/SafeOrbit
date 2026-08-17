package ru.wizand.safeorbit.data.service

import android.app.Notification
import android.os.Handler
import android.os.HandlerThread
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.repository.NotificationRepositoryImpl
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager
import ru.wizand.safeorbit.data.utils.AllowedAppsPreferences
import ru.wizand.safeorbit.data.utils.AppListUtils

/**
 * Перехватывает системные уведомления выбранных приложений и публикует их
 * в зашифрованном виде в Firebase для клиента.
 *
 * Дебаунс: не чаще одного уведомления на пакет в 15 секунд.
 */
class NotificationLoggerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationLogger"
        private const val DEBOUNCE_MS = 15_000L
        private const val MAX_TEXT_LENGTH = 4000
    }

    private val repository = NotificationRepositoryImpl()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lastNotificationTimePerPackage = mutableMapOf<String, Long>()
    private val pendingTasks = mutableMapOf<String, Runnable>()

    private val handlerThread = HandlerThread("NotificationDelayThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        // Свои уведомления не перехватываем
        if (packageName == this.packageName) return

        if (packageName !in AllowedAppsPreferences.getAllowedPackages(applicationContext)) {
            Log.d(TAG, "Уведомление от $packageName проигнорировано")
            return
        }

        val now = System.currentTimeMillis()
        val lastSentTime = lastNotificationTimePerPackage[packageName] ?: 0L

        if (now - lastSentTime >= DEBOUNCE_MS) {
            cancelPendingTask(packageName)
            lastNotificationTimePerPackage[packageName] = now
            publish(sbn)
        } else {
            val remaining = DEBOUNCE_MS - (now - lastSentTime)
            cancelPendingTask(packageName)
            val runnable = Runnable {
                lastNotificationTimePerPackage[packageName] = System.currentTimeMillis()
                pendingTasks.remove(packageName)
                publish(sbn)
            }
            pendingTasks[packageName] = runnable
            handler.postDelayed(runnable, remaining)
        }
    }

    private fun publish(sbn: StatusBarNotification) {
        val encryptedPrefs = EncryptedPreferencesManager(applicationContext)
        val serverId = encryptedPrefs.getServerId()
        val code = encryptedPrefs.getCode()
        if (serverId.isNullOrBlank() || code.isNullOrBlank()) {
            Log.w(TAG, "Сервер не зарегистрирован — публикация уведомления пропущена")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(MAX_TEXT_LENGTH) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(MAX_TEXT_LENGTH) ?: ""
        val appLabel = AppListUtils.getAppLabel(applicationContext, sbn.packageName)

        scope.launch {
            repository.publish(
                serverId = serverId,
                code = code,
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = title,
                text = text,
                postTime = sbn.postTime
            ).onFailure { e ->
                Log.e(TAG, "❌ Ошибка публикации уведомления: ${e.message}")
            }
        }
    }

    private fun cancelPendingTask(packageName: String) {
        pendingTasks.remove(packageName)?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingTasks.values.forEach { handler.removeCallbacks(it) }
        pendingTasks.clear()
        handlerThread.quitSafely()
        scope.cancel()
        Log.d(TAG, "NotificationLoggerService уничтожен и ресурсы очищены")
    }
}
