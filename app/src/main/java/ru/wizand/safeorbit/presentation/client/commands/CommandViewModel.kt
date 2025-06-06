package ru.wizand.safeorbit.presentation.client.commands

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.AppDatabase
import javax.inject.Inject

@HiltViewModel
class CommandViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {

    fun requestLocationUpdate(serverId: String) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId) ?: return@launch
            val ref = FirebaseDatabase.getInstance()
                .getReference("server_commands/$serverId")
                .push()
            val data = mapOf("code" to server.code, "request_location_update" to true)
            ref.setValue(data)
        }
    }

    fun sendServerSettings(serverId: String, activeMs: Long, idleMs: Long) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId) ?: return@launch
            val code = server.code
            val ref = FirebaseDatabase.getInstance()
                .getReference("server_commands/$serverId")
                .push()
            val data = mapOf(
                "code" to code,
                "update_settings" to mapOf(
                    "active_interval" to activeMs,
                    "inactivity_timeout" to idleMs
                )
            )
            ref.setValue(data).addOnCompleteListener {
                if (!it.isSuccessful) {
                    Log.e("CLIENT_CMD", "❌ Ошибка при отправке команды: ${it.exception}")
                }
            }
        }
    }

    fun startAudioStream(serverId: String, onCodeReady: (String) -> Unit) {
        viewModelScope.launch {
            val server = db.serverDao().getByServerId(serverId) ?: return@launch
            val code = server.code
            val ref = FirebaseDatabase.getInstance()
                .getReference("server_commands/$serverId")
                .push()
            val data = mapOf(
                "code" to code,
                "type" to "START_AUDIO_STREAM",
                "timestamp" to System.currentTimeMillis()
            )
            ref.setValue(data).addOnSuccessListener {
                onCodeReady(code)
            }.addOnFailureListener {
                Log.e("CLIENT_CMD", "❌ Ошибка при старте аудио: ${it.message}")
            }
        }
    }

    fun stopAudioStream(serverId: String, code: String) {
        val ref = FirebaseDatabase.getInstance()
            .getReference("server_commands/$serverId")
            .push()
        val data = mapOf(
            "code" to code,
            "type" to "STOP_AUDIO_STREAM",
            "timestamp" to System.currentTimeMillis()
        )
        ref.setValue(data)
    }
}
