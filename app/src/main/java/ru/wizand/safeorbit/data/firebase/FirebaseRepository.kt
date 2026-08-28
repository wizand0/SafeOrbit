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

    companion object {
        /**
         * Допуск на рассинхрон часов клиента и сервера при проверке срока действия токена.
         * Без него связывание падало с «токен просрочен» при спешащих часах клиента,
         * а со второй попытки проходило (кэш/повторный запуск успевал «переждать» разницу).
         */
        private const val EXPIRY_GRACE_MS = 60_000L
    }

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    init {
        ensureAuthenticated()
    }

    private fun ensureAuthenticated() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            android.util.Log.d("FirebaseRepository", "✅ Пользователь уже аутентифицирован: ${currentUser.uid}")
            val encryptedPrefs = ru.wizand.safeorbit.data.security.EncryptedPreferencesManager.getInstance(context)
            if (encryptedPrefs.getAnonymousUid() == null) {
                encryptedPrefs.saveAnonymousUid(currentUser.uid)
            }
        } else {
            signInAnonymously()
        }
    }
    
    private fun signInAnonymously() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val encryptedPrefs = ru.wizand.safeorbit.data.security.EncryptedPreferencesManager.getInstance(context)
                    encryptedPrefs.saveAnonymousUid(currentUser.uid)
                    android.util.Log.d("FirebaseRepository", "✅ Анонимная аутентификация успешна, UID сохранен: ${currentUser.uid}")
                }
            } else {
                android.util.Log.e("FirebaseRepository", "❌ Ошибка анонимной аутентификации: ${task.exception?.message}")
            }
        }
    }

    fun registerServer(onComplete: (serverId: String, pairingToken: String) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            performRegistration(onComplete)
        } else {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    performRegistration(onComplete)
                } else {
                    android.util.Log.e("FirebaseRepository", "❌ Не удалось аутентифицироваться для регистрации сервера: ${task.exception?.message}")
                }
            }
        }
    }

    private fun performRegistration(onComplete: (serverId: String, pairingToken: String) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            android.util.Log.e("FirebaseRepository", "❌ Невозможно зарегистрировать сервер: пользователь не авторизован")
            return
        }
        
        val serverId = generateReadableId(context)
        val tokenBytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val pairingToken = android.util.Base64.encodeToString(tokenBytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        
        val serverData = mapOf(
            "ownerUid" to currentUser.uid,
            "pairing" to mapOf(
                "tokenHash" to ru.wizand.safeorbit.utils.CryptoUtils.sha256Hex(pairingToken),
                "expiresAt" to (System.currentTimeMillis() + 10 * 60 * 1000L), // 10 минут
                "consumed" to false
            ),
            "location" to null
        )
        
        val encryptedPrefs = ru.wizand.safeorbit.data.security.EncryptedPreferencesManager.getInstance(context)
        encryptedPrefs.saveAnonymousUid(currentUser.uid)
        
        db.child("servers").child(serverId).setValue(serverData)
            .addOnSuccessListener { 
                android.util.Log.d("FirebaseRepository", "✅ Сервер зарегистрирован: $serverId")
                onComplete(serverId, pairingToken) 
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("FirebaseRepository", "❌ Ошибка регистрации сервера: ${exception.message}", exception)
            }
    }

    fun pairClientToServer(serverId: String, token: String, onResult: (Boolean) -> Unit) {
        android.util.Log.d("FirebaseRepository", "🔄 pairClientToServer: serverId=$serverId")
        val serverRef = db.child("servers").child(serverId)
        
        // Прогреваем локальный кэш перед транзакцией.
        // Без этого runTransaction видит пустой кэш, делает abort() локально и не идет на сервер.
        serverRef.get().addOnSuccessListener {
            android.util.Log.d("FirebaseRepository", "📥 Кэш прогрет, запуск транзакции")
            serverRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                android.util.Log.d("FirebaseRepository", "🔄 Транзакция запущена для $serverId")
                val hash = currentData.child("pairing/tokenHash").getValue(String::class.java)
                val expiresAt = currentData.child("pairing/expiresAt").getValue(Long::class.java) ?: 0L
                val consumed = currentData.child("pairing/consumed").getValue(Boolean::class.java) ?: true
                
                android.util.Log.d("FirebaseRepository", "📊 Данные из БД: hash=$hash, expiresAt=$expiresAt, consumed=$consumed")
                val tokenHash = ru.wizand.safeorbit.utils.CryptoUtils.sha256Hex(token)
                android.util.Log.d("FirebaseRepository", "🔑 Вычисленный hash токена: $tokenHash")
                
                if (hash != tokenHash) {
                    android.util.Log.e("FirebaseRepository", "❌ Hash не совпадает: expected=$hash, actual=$tokenHash")
                    return Transaction.abort()
                }
                if (consumed) {
                    android.util.Log.e("FirebaseRepository", "❌ Токен уже использован (consumed=true)")
                    return Transaction.abort()
                }
                if (expiresAt + EXPIRY_GRACE_MS <= System.currentTimeMillis()) {
                    android.util.Log.e("FirebaseRepository", "❌ Токен просрочен: expiresAt=$expiresAt, now=${System.currentTimeMillis()}, grace=$EXPIRY_GRACE_MS")
                    return Transaction.abort()
                }
                
                android.util.Log.d("FirebaseRepository", "✅ Все проверки пройдены, устанавливаем consumed=true")
                currentData.child("pairing/consumed").value = true
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                android.util.Log.d("FirebaseRepository", "🏁 Транзакция завершена: error=$error, committed=$committed")
                if (error != null) {
                    android.util.Log.e("FirebaseRepository", "❌ Ошибка транзакции: ${error.message}", error.toException())
                    onResult(false)
                    return
                }
                if (!committed) {
                    android.util.Log.e("FirebaseRepository", "❌ Транзакция не зафиксирована (прервана)")
                    onResult(false)
                    return
                }
                
                val clientId = auth.currentUser?.uid
                android.util.Log.d("FirebaseRepository", "👤 ClientId: $clientId")
                if (clientId.isNullOrBlank()) {
                    android.util.Log.e("FirebaseRepository", "❌ ClientId пустой")
                    onResult(false)
                    return
                }
                
                android.util.Log.d("FirebaseRepository", "💾 Запись в server_clients/$serverId/$clientId")
                // Записываем в server_clients, чтобы сервер мог управлять доступом
                db.child("server_clients").child(serverId).child(clientId)
                    .setValue(true)
                    .addOnSuccessListener {
                        android.util.Log.d("FirebaseRepository", "✅ Успешно записано в server_clients")
                        onResult(true)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("FirebaseRepository", "❌ Ошибка записи в server_clients: ${e.message}", e)
                        onResult(false)
                    }
            }
            })
        }.addOnFailureListener { e ->
            android.util.Log.e("FirebaseRepository", "❌ Ошибка чтения сервера перед транзакцией: ${e.message}")
            onResult(false)
        }
    }

    /** Состояние пары привязки для экрана QR-кода. */
    data class PairingState(val consumed: Boolean, val expiresAt: Long)

    /**
     * FIX: повторная привязка после истечения/использования кода.
     * Изначально pairing писался ОДИН раз при регистрации (tokenHash,
     * expiresAt = +10 минут, consumed=false) и больше никогда не обновлялся:
     * после consumed=true или просрочки новый QR не привязывался —
     * помогала только переустановка сервера. Ротация перевыпускает токен
     * (права на запись в узел servers/$serverId у владельца есть по .write).
     */
    fun rotatePairing(serverId: String, onResult: (pairingToken: String?) -> Unit) {
        if (auth.currentUser == null) {
            android.util.Log.e("FirebaseRepository", "❌ Ротация pairing: нет авторизации")
            onResult(null)
            return
        }
        val tokenBytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val pairingToken = android.util.Base64.encodeToString(
            tokenBytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        )
        val updates: Map<String, Any> = mapOf(
            "pairing/tokenHash" to ru.wizand.safeorbit.utils.CryptoUtils.sha256Hex(pairingToken),
            "pairing/expiresAt" to (System.currentTimeMillis() + 10 * 60 * 1000L),
            "pairing/consumed" to false
        )
        db.child("servers").child(serverId).updateChildren(updates)
            .addOnSuccessListener {
                android.util.Log.d("FirebaseRepository", "✅ Pairing-токен перевыпущен для $serverId")
                onResult(pairingToken)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FirebaseRepository", "❌ Ошибка ротации pairing: ${e.message}")
                onResult(null)
            }
    }

    /** Чтение состояния pairing (для статуса в диалоге QR-кода). */
    fun fetchPairingState(serverId: String, onResult: (PairingState?) -> Unit) {
        db.child("servers").child(serverId).child("pairing")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val consumed = snapshot.child("consumed").getValue(Boolean::class.java) ?: false
                    val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: 0L
                    onResult(PairingState(consumed, expiresAt))
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("FirebaseRepository", "❌ Ошибка чтения pairing: ${error.message}")
                    onResult(null)
                }
            })
    }

    fun revokeAccess(serverId: String, clientUid: String, onComplete: (Boolean) -> Unit) {
        // Удаляем из server_clients
        db.child("server_clients").child(serverId).child(clientUid)
            .removeValue()
            .addOnSuccessListener {
                android.util.Log.d("FirebaseRepository", "✅ Доступ клиента $clientUid к серверу $serverId отозван (server_clients)")
                // Также удаляем из temp_access на всякий случай
                db.child("temp_access").child(clientUid).child(serverId)
                    .removeValue()
                    .addOnSuccessListener {
                        android.util.Log.d("FirebaseRepository", "✅ Доступ клиента $clientUid к серверу $serverId отозван (temp_access)")
                        onComplete(true)
                    }
                    .addOnFailureListener {
                        android.util.Log.e("FirebaseRepository", "❌ Ошибка отзыва доступа (temp_access): ${it.message}")
                        onComplete(false)
                    }
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("FirebaseRepository", "❌ Ошибка отзыва доступа (server_clients): ${exception.message}")
                onComplete(false)
            }
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
                android.util.Log.e("FIREBASE", "❌ Ошибка при отправке координат: ${it.message}", it)
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
                    android.util.Log.w("CLIENT", "📭 Нет координат в БД для $serverId (value: ${snapshot.value})")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("CLIENT", "❌ Ошибка подписки на координаты $serverId: ${error.message}")
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