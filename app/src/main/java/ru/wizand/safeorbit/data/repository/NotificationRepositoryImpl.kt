package ru.wizand.safeorbit.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ru.wizand.safeorbit.data.model.AppNotification
import ru.wizand.safeorbit.domain.repository.NotificationRepository
import ru.wizand.safeorbit.utils.CryptoUtils
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor() : NotificationRepository {

    companion object {
        private const val TAG = "NotifRepo"
    }

    private val db = FirebaseDatabase.getInstance()

    private fun nodeRef(serverId: String) =
        db.getReference("servers").child(serverId).child("app_notifications")

    override suspend fun publish(
        serverId: String,
        pairingToken: String,  // используем pairingToken вместо кода
        packageName: String,
        appLabel: String,
        title: String,
        text: String,
        postTime: Long
    ): Result<Unit> = runCatching {
        val key = CryptoUtils.deriveKey(serverId, pairingToken)  // используем pairingToken вместо кода
        val payload = mapOf(
            "enc_pkg" to CryptoUtils.encrypt(key, packageName),
            "enc_label" to CryptoUtils.encrypt(key, appLabel),
            "enc_title" to CryptoUtils.encrypt(key, title),
            "enc_text" to CryptoUtils.encrypt(key, text),
            "post_time" to postTime
        )
        nodeRef(serverId).push().setValue(payload).await()
        trimOldEntries(serverId)
    }

    /** Держим в Firebase не больше MAX_STORED записей: удаляем самые старые. */
    private suspend fun trimOldEntries(serverId: String) {
        val ref = nodeRef(serverId)
        val snapshot = ref.get().await()
        val children = snapshot.children.toList()
        if (children.size <= NotificationRepository.MAX_STORED) return

        val sorted = children.sortedBy { it.child("post_time").getValue(Long::class.java) ?: 0L }
        val excess = sorted.take(children.size - NotificationRepository.MAX_STORED)
        for (child in excess) {
            child.ref.removeValue().await()
        }
        Log.d(TAG, "🧹 Удалено ${excess.size} старых уведомлений для $serverId")
    }

    override fun observe(serverId: String, pairingToken: String): Flow<List<AppNotification>> = callbackFlow {
        val key = CryptoUtils.deriveKey(serverId, pairingToken)  // используем pairingToken вместо кода

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val firebaseKey = child.key ?: return@mapNotNull null
                    val postTime = child.child("post_time").getValue(Long::class.java) ?: return@mapNotNull null
                    val encPkg = child.child("enc_pkg").getValue(String::class.java)
                    val encLabel = child.child("enc_label").getValue(String::class.java)
                    val encTitle = child.child("enc_title").getValue(String::class.java)
                    val encText = child.child("enc_text").getValue(String::class.java)
                    if (encPkg == null || encLabel == null || encTitle == null || encText == null) return@mapNotNull null

                    val pkg = CryptoUtils.decryptOrNull(key, encPkg) ?: return@mapNotNull null
                    val label = CryptoUtils.decryptOrNull(key, encLabel) ?: return@mapNotNull null
                    val title = CryptoUtils.decryptOrNull(key, encTitle) ?: return@mapNotNull null
                    val text = CryptoUtils.decryptOrNull(key, encText) ?: return@mapNotNull null

                    AppNotification(firebaseKey, pkg, label, title, text, postTime)
                }
                    .sortedByDescending { it.postTime }
                    .take(NotificationRepository.VISIBLE_LIMIT)

                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Ошибка подписки на уведомления $serverId: ${error.message}")
                trySend(emptyList())
            }
        }

        nodeRef(serverId).addValueEventListener(listener)
        awaitClose { nodeRef(serverId).removeEventListener(listener) }
    }

    override suspend fun clear(serverId: String, pairingToken: String): Result<Unit> = runCatching {  // используем pairingToken вместо кода
        nodeRef(serverId).removeValue().await()
    }
}
