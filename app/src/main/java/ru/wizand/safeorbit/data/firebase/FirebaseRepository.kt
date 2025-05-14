package ru.wizand.safeorbit.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import ru.wizand.safeorbit.data.model.AudioRequest
import ru.wizand.safeorbit.data.model.LocationData
import java.util.UUID

class FirebaseRepository {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    init {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
    }

    fun registerServer(onComplete: (serverId: String, code: String) -> Unit) {
        val serverId = UUID.randomUUID().toString()
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
                    snapshot.getValue(LocationData::class.java)?.let(onUpdate)
                }

                override fun onCancelled(error: DatabaseError) {}
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
}