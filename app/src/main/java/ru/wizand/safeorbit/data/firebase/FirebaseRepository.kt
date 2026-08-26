package ru.wizand.safeorbit.data.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.utils.generateReadableId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 1.6: @Inject constructor + @Singleton — Hilt создаёт и переиспользует ОДИН
 * экземпляр на весь процесс, вместо `FirebaseRepository(applicationContext)`
 * на каждый вызов из LocationService/IdleLocationWorker.
 *
 * Если в проекте уже есть FirebaseModule.provideFirebaseRepository(), можно
 * оставить любой из двух вариантов, но не оба — иначе Hilt выдаст ошибку
 * дублирующегося биндинга при сборке графа.
 */
@Singleton
class FirebaseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    init {
        ensureAuthenticated()
    }

    private fun ensureAuthenticated() {
        if (auth.currentUser != null) return
        
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                android.util.Log.d("FirebaseRepository", "✅ Анонимная аутентификация успешна: ${'$'}{auth.currentUser?.uid}")
            } else {
                android.util.Log.e("FirebaseRepository", "❌ Ошибка анонимной аутентификации: ${'$'}{task.exception?.message}")
            }
        }
    }

    fun registerServer(onComplete: (serverId: String, pairingToken: String) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            ensureAuthenticated()
            // Ждем аутентификации перед регистрацией сервера
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    performRegistration(onComplete)
                } else {
                    android.util.Log.e("FirebaseRepository", "❌ Не удалось аутентифицироваться для регистрации сервера: ${'$'}{task.exception?.message}")
                    // В любом случае пробуем зарегистрировать, т.к. может быть anon auth уже работает
                    performRegistration(onComplete)
                }
            }
        } else {
            performRegistration(onComplete)
        }
    }

    private fun performRegistration(onComplete: (serverId: String, pairingToken: String) -> Unit) {
        val serverId = generateReadableId(context)
        val tokenBytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val pairingToken = android.util.Base64.encodeToString(tokenBytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        
        val serverData = mapOf(
            "ownerUid" to (auth.currentUser?.uid ?: ""),
            "pairing" to mapOf(
                "tokenHash" to ru.wizand.safeorbit.utils.CryptoUtils.sha256Hex(pairingToken),
                "expiresAt" to (System.currentTimeMillis() + 10 * 60 * 1000L), // 10 минут
                "consumed" to false
            ),
            "location" to null
        )
        
        db.child("servers").child(serverId).setValue(serverData)
            .addOnSuccessListener { onComplete(serverId, pairingToken) }
            .addOnFailureListener { exception ->
                android.util.Log.e("FirebaseRepository", "❌ Ошибка регистрации сервера: ${'$'}{exception.message}", exception)
            }
    }

    fun pairClientToServer(serverId: String, token: String, onResult: (Boolean) -> Unit) {
        ensureAuthenticated()
        val serverRef = db.child("servers").child(serverId)
        serverRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val hash = currentData.child("pairing/tokenHash").getValue(String::class.java)
                val expiresAt = currentData.child("pairing/expiresAt").getValue(Long::class.java) ?: 0L
                val consumed = currentData.child("pairing/consumed").getValue(Boolean::class.java) ?: true
                if (hash != ru.wizand.safeorbit.utils.CryptoUtils.sha256Hex(token) ||
                    consumed || expiresAt <= System.currentTimeMillis()
                ) return Transaction.abort()
                currentData.child("pairing/consumed").value = true
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null || !committed) {
                    onResult(false)
                    return
                }
                val clientId = auth.currentUser?.uid
                if (clientId.isNullOrBlank()) {
                    onResult(false)
                    return
                }
                db.child("clients").child(clientId).child("linked_servers").child(serverId)
                    .setValue(true)
                    .addOnCompleteListener { onResult(it.isSuccessful) }
            }
        })
    }

    fun sendLocation(serverId: String, location: LocationData) {
        ensureAuthenticated()
        val user = auth.currentUser
        if (user == null) {
            android.util.Log.e("FIREBASE", "❌ Невозможно отправить координаты: пользователь не авторизован")
            return
        }

        db.child("servers").child(serverId).child("location").setValue(location)
            .addOnFailureListener {
                android.util.Log.e("FIREBASE", "❌ Ошибка при отправке координат: ${'$'}{it.message}", it)
            }
            .addOnSuccessListener {
                android.util.Log.d("FIREBASE", "✅ Координаты успешно отправлены")
            }
    }

    /**
     * Подписка на координаты сервера.
     *
     * ВАЖНО: возвращает ValueEventListener, чтобы вызывающий код мог снять подписку.
     * Повторный вызов для того же serverId без снятия предыдущего listener приводит
     * к дублированию обработки координат. Управление активными подписками — в ServerMapViewModel.
     *
     * Внутри выполняется dedup по содержимому: координата с теми же
     * latitude/longitude/timestamp не пропускается повторно (аналог distinctUntilChanged).
     */
    fun observeServerLocation(serverId: String, onUpdate: (LocationData) -> Unit): ValueEventListener {
        var lastSeen: LocationData? = null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(LocationData::class.java)
                if (location != null) {
                    if (lastSeen == location) {
                        android.util.Log.d("CLIENT", "⏭ Дубликат координаты $serverId пропущен")
                        return
                    }
                    lastSeen = location
                    android.util.Log.d("CLIENT", "📍 Получена координата $serverId (данные скрыты)")
                    onUpdate(location)
                } else {
                    android.util.Log.w("CLIENT", "📭 Нет координат в БД для $serverId (value: ${'$'}{snapshot.value})")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("CLIENT", "❌ Ошибка подписки на координаты $serverId: ${'$'}{error.message}")
            }
        }
        db.child("servers").child(serverId).child("location").addValueEventListener(listener)
        return listener
    }

    /**
     * Снять подписку на координаты сервера.
     */
    fun stopObservingServerLocation(serverId: String, listener: ValueEventListener) {
        db.child("servers").child(serverId).child("location").removeEventListener(listener)
    }

    fun generateUniqueServerId(
        onReady: (uniqueId: String) -> Unit,
        retryCount: Int = 0,
        maxRetries: Int = 10
    ) {
        if (retryCount >= maxRetries) {
            return onReady("ERROR")
        }

        val candidateId = generateReadableId(context)
        val ref = FirebaseDatabase.getInstance()
            .getReference("servers")
            .child(candidateId)

        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                generateUniqueServerId(onReady, retryCount + 1, maxRetries)
            } else {
                onReady(candidateId)
            }
        }.addOnFailureListener {
            generateUniqueServerId(onReady, retryCount + 1, maxRetries)
        }
    }
}