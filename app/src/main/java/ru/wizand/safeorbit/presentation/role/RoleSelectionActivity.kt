package ru.wizand.safeorbit.presentation.role

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.databinding.ActivityRoleSelectionBinding
import ru.wizand.safeorbit.presentation.client.ClientMainActivity
import ru.wizand.safeorbit.presentation.server.ServerMainActivity

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding
    private val viewModel: RoleSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.progressAuth.visibility = View.VISIBLE
        signInAnonymouslyIfNeeded()
        checkFirebaseConnection()

        // ⬇️ Автопереход, если уже выбрана роль
        viewModel.getUserRole()?.let { role ->
            when (role) {
                UserRole.SERVER -> {
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    val pin = prefs.getString("server_pin", null)
                    val verified = prefs.getBoolean("pin_verified", false)

                    val intent = if (pin != null && !verified) {
                        Intent(this, ru.wizand.safeorbit.presentation.security.PinGateActivity::class.java)
                    } else {
                        Intent(this, ServerMainActivity::class.java)
                    }

                    startActivity(intent)
                    finish()
                    return
                }

                UserRole.CLIENT -> {
                    startActivity(Intent(this, ClientMainActivity::class.java))
                    finish()
                    return
                }
            }
        }

        // если роль не выбрана — показываем выбор
        binding.btnServer.setOnClickListener {
            viewModel.saveUserRole(UserRole.SERVER)
            saveUserRoleToDatabase("server")

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val pin = prefs.getString("server_pin", null)
            val verified = prefs.getBoolean("pin_verified", false)

            val intent = if (pin != null && !verified) {
                Intent(this, ru.wizand.safeorbit.presentation.security.PinGateActivity::class.java)
            } else {
                Intent(this, ServerMainActivity::class.java)
            }

            startActivity(intent)
        }

        binding.btnClient.setOnClickListener {
            viewModel.saveUserRole(UserRole.CLIENT)
            saveUserRoleToDatabase("client")
            startActivity(Intent(this, ClientMainActivity::class.java))
        }
    }

    private fun signInAnonymouslyIfNeeded() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    binding.progressAuth.visibility = View.GONE
                    Log.d("AUTH", "Анонимный вход выполнен")
                }
                .addOnFailureListener {
                    binding.progressAuth.visibility = View.GONE
                    Toast.makeText(this, "Ошибка входа: ${it.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            binding.progressAuth.visibility = View.GONE
        }
    }

    private fun saveUserRoleToDatabase(role: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        db.child("users").child(uid).setValue(mapOf("role" to role))
    }

    private fun checkFirebaseConnection() {
        val db = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        val connectedRef = db.child(".info/connected")
        connectedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (!connected) {
                    Toast.makeText(this@RoleSelectionActivity, "Нет подключения к Firebase", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(this@RoleSelectionActivity, "Ошибка подключения: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }





}
