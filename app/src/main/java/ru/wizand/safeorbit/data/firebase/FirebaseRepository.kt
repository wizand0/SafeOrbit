package ru.wizand.safeorbit.data.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import ru.wizand.safeorbit.data.model.AudioRequest
import ru.wizand.safeorbit.data.model.LocationData
import ru.wizand.safeorbit.utils.Constants
import ru.wizand.safeorbit.utils.generateReadableId
import java.util.UUID

class FirebaseRepository(private val context: Context) {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    init {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }

    fun registerServer(onComplete: (serverId: String, code: String) -> Unit) {
        val serverId = generateReadableId(context) // вместо UUID.randomUUID().toString()
        val code = (100000..999999).random().toString()
        val serverData = mapOf(
            "code" to code,
            "location" to null,
            "audio_request" to null
        )
        db.child("servers").child(serverId).setValue(serverData)
            .addOnSuccessListener { onComplete(serverId, code) }
    }

    fun pairClientToServer(serverId: String, code: String, onResult: (Boolean) -> Unit) {
        db.child("servers").child(serverId).child("code").get()
            .addOnSuccessListener {
                val match = it.getValue(String::class.java) == code
                if (match) {
                    val clientId = auth.currentUser?.uid ?: return@addOnSuccessListener
                    db.child("clients").child(clientId).child("linked_servers").child(serverId).setValue(true)
                        .addOnSuccessListener { onResult(true) }
                } else {
                    onResult(false)
                }
            }.addOnFailureListener { onResult(false) }
    }

    fun sendLocation(serverId: String, location: LocationData) {
        db.child("servers").child(serverId).child("location").setValue(location)
    }

    fun observeServerLocation(serverId: String, onUpdate: (LocationData) -> Unit) {
        db.child("servers").child(serverId).child("location")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val location = snapshot.getValue(LocationData::class.java)
                    if (location != null) {
                        android.util.Log.d("CLIENT", "📍 FirebaseRepository: получена координата $serverId -> $location")
                        onUpdate(location)
                    } else {
                        android.util.Log.d("CLIENT", "📭 FirebaseRepository: пустая координата для $serverId")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("CLIENT", "❌ FirebaseRepository: ошибка получения координаты для $serverId: ${error.message}")
                }
            })
    }

    fun sendAudioRequest(serverId: String) {
        val clientId = auth.currentUser?.uid ?: return
        val request = AudioRequest(clientId, System.currentTimeMillis())
        db.child("servers").child(serverId).child("audio_request").setValue(request)
    }

    fun observeAudioRequest(serverId: String, onRequest: (AudioRequest) -> Unit) {
        db.child("servers").child(serverId).child("audio_request")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.getValue(AudioRequest::class.java)?.let(onRequest)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun generateUniqueServerId(
        onReady: (uniqueId: String) -> Unit,
        retryCount: Int = 0,
        maxRetries: Int = 10
    ) {
        if (retryCount >= maxRetries) {
            // Слишком много попыток
            return onReady("ERROR")
        }

        val candidateId = generateReadableId(context)
        val ref = FirebaseDatabase.getInstance(Constants.FIREBASE_DB_URL)
            .getReference("servers")
            .child(candidateId)

        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Уже занят — пробуем снова
                generateUniqueServerId(onReady, retryCount + 1, maxRetries)
            } else {
                // Уникален — используем его
                onReady(candidateId)
            }
        }.addOnFailureListener {
            // В случае ошибки — пробуем снова
            generateUniqueServerId(onReady, retryCount + 1, maxRetries)
        }
    }




}