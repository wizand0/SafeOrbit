package ru.wizand.safeorbit.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import ru.wizand.safeorbit.data.model.AudioRequest

class AudioRemoteDataSource(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    fun observeAudioRequest(serverId: String, onRequest: (AudioRequest) -> Unit) {
        db.child("servers").child(serverId).child("audio_request")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.getValue(AudioRequest::class.java)?.let { onRequest(it) }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun sendAudioRequest(serverId: String, fromClientId: String) {
        val request = AudioRequest(
            fromClient = fromClientId,
            timestamp = System.currentTimeMillis()
        )
        db.child("servers").child(serverId).child("audio_request").setValue(request)
    }

    fun uploadAudioFile(
        serverId: String,
        clientId: String,
        audioBytes: ByteArray,
        onSuccess: (url: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val path = "audio/${serverId}_${System.currentTimeMillis()}.3gp"
        val ref = storage.reference.child(path)

        ref.putBytes(audioBytes)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    // Сохраняем ссылку клиенту
                    db.child("clients")
                        .child(clientId)
                        .child("audio_responses")
                        .child(serverId)
                        .setValue(mapOf("url" to uri.toString(), "timestamp" to System.currentTimeMillis()))
                    onSuccess(uri.toString())
                }
            }
            .addOnFailureListener(onFailure)
    }
}
