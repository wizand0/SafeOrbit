package ru.wizand.safeorbit.presentation.role

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import ru.wizand.safeorbit.data.model.UserRole
import ru.wizand.safeorbit.databinding.ActivityRoleSelectionBinding
import ru.wizand.safeorbit.presentation.client.ClientMainActivity
import ru.wizand.safeorbit.presentation.security.PinGateActivity
import ru.wizand.safeorbit.presentation.server.ServerMainActivity
import ru.wizand.safeorbit.utils.Constants.PREFS_NAME

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding
    private val viewModel: RoleSelectionViewModel by viewModels()


    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>

    private val deniedPermissions = mutableListOf<String>()
    private var currentPermissionIndex = 0
    private var hasPermanentlyDenied = false

    //    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var clientPermissionLauncher: ActivityResultLauncher<Array<String>>

    private val serverPermissionsToRequest: Array<String> by lazy {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, // важно, чтобы ACCESS_FINE работал корректно
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms += Manifest.permission.ACCESS_BACKGROUND_LOCATION
            perms += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            perms += Manifest.permission.FOREGROUND_SERVICE_LOCATION
            perms += Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            perms += Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
        }
        perms.toTypedArray()
    }


    private val clientPermissionsToRequest: Array<String> by lazy {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms += Manifest.permission.ACTIVITY_RECOGNITION
        }
        perms.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            val permission = serverPermissionsToRequest[currentPermissionIndex]

            if (isGranted) {
                Log.d("PERMISSION_TEST", "✅ Разрешение $permission выдано")
            } else {
                val permanentlyDenied = !shouldShowRequestPermissionRationale(permission)
                if (permanentlyDenied) {
                    Log.d("PERMISSION_TEST", "⛔ $permission запрещено навсегда")
                    hasPermanentlyDenied = true
                } else {
                    Log.d("PERMISSION_TEST", "❌ $permission не выдано")
                }
                deniedPermissions.add(permission)
            }

            currentPermissionIndex++
            requestNextPermission()
        }


        clientPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val denied = result.filterValues { !it }
            if (denied.isEmpty()) {
                launchClient()
            } else {
                Toast.makeText(this, "Не все разрешения выданы", Toast.LENGTH_LONG).show()
            }
        }

        // Launcher для открытия настроек
        settingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            Log.d("PERMISSION_TEST", "🔁 Возврат из настроек, повторная проверка")
            checkAndRequestPermissions()
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val fromReset = intent.getBooleanExtra("fromReset", false)

        if (!fromReset) {
            viewModel.getUserRole()?.let { role ->
                when (role) {
                    UserRole.SERVER -> {
                        val pin = prefs.getString("server_pin", null)
                        val verified = prefs.getBoolean("pin_verified", false)
                        val intent = if (pin != null && !verified) {
                            Intent(this, PinGateActivity::class.java)
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
        }

        binding.progressAuth.visibility = View.VISIBLE
        signInAnonymouslyIfNeeded()
        checkFirebaseConnection()

        binding.btnServer.setOnClickListener {
            showPermissionsIntroAndRequest()
        }

        binding.btnClient.setOnClickListener {
            showClientPermissionDialog()
        }
    }

    private fun signInAnonymouslyIfNeeded() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    binding.progressAuth.visibility = View.GONE
                    Log.d("AUTH", "✅ Анонимный вход выполнен")
                    checkFirebaseConnection()
                }
                .addOnFailureListener {
                    binding.progressAuth.visibility = View.GONE
                    Toast.makeText(this, "Ошибка входа: ${it.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            binding.progressAuth.visibility = View.GONE
            checkFirebaseConnection()
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
        connectedRef.addValueEventListener(object :
            com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (!connected) {
                    Toast.makeText(
                        this@RoleSelectionActivity,
                        "Идет подключение к серверу",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(
                    this@RoleSelectionActivity,
                    "Ошибка подключения: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun showPermissionsIntroAndRequest() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Разрешения для сервера")
            .setMessage(
                """
                Перед началом работы в роли сервера нужно выдать разрешения:

                • Геолокация — для определения положения.
                • Микрофон — для аудиотрансляции.
                • Камера — для установки иконки.
                • Распознавание активности — для экономии энергии.
                • Фоновый доступ — чтобы работать при выключенном экране.
                """.trimIndent()
            )
            .setPositiveButton("Продолжить") { _, _ ->
                Log.d("PERMISSION_TEST", "📋 Пользователь согласился")
                binding.root.postDelayed({
                    checkAndRequestPermissions()

                }, 500)
            }
            .setCancelable(false)
            .show()
    }

    private fun checkAndRequestPermissions() {
        deniedPermissions.clear()
        hasPermanentlyDenied = false
        currentPermissionIndex = 0
        requestNextPermission()
    }

    private fun requestNextPermission() {
        if (currentPermissionIndex >= serverPermissionsToRequest.size) {
            if (deniedPermissions.isEmpty()) {
                Log.d("PERMISSION_TEST", "✅ Все разрешения выданы")
                proceedToServer()
            } else {
                Log.d("PERMISSION_TEST", "⛔ Не выданы: ${deniedPermissions.joinToString()}")
                if (hasPermanentlyDenied) {
                    Log.d("PERMISSION_TEST", "⚠️ Есть навсегда запрещённые разрешения")
                    showGoToSettingsDialog()
                } else {
                    Toast.makeText(this, "Не все разрешения выданы", Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        val permission = serverPermissionsToRequest[currentPermissionIndex]
        val granted =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        Log.d("PERMISSION_TEST", "🔎 Проверяем $permission: ${if (granted) "OK" else "НЕ ОК"}")

        if (granted) {
            currentPermissionIndex++
            requestNextPermission()
        } else {
            val shouldShow = shouldShowRequestPermissionRationale(permission)
            Log.d("PERMISSION_TEST", "🔍 $permission: denied=true, showRationale=$shouldShow")

            permissionLauncher.launch(permission)
        }
    }

    private fun showGoToSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Разрешения отключены")
            .setMessage("Вы ранее запретили доступ к разрешениям. Перейдите в настройки приложения, чтобы включить их вручную.")
            .setPositiveButton("Настройки") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun proceedToServer() {
        Log.d("PERMISSION_TEST", "➡ Переход в режим сервера")
        viewModel.saveUserRole(UserRole.SERVER)
        saveUserRoleToDatabase("server")

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val pin = prefs.getString("server_pin", null)
        val verified = prefs.getBoolean("pin_verified", false)

        Log.d("PERMISSION_TEST", "🛡 PIN: ${pin != null}, verified=$verified")

        val intent = if (pin != null && !verified) {
            Log.d("PERMISSION_TEST", "🔑 Открываем PinGateActivity")
            Intent(this, PinGateActivity::class.java)
        } else {
            Log.d("PERMISSION_TEST", "🏠 Открываем ServerMainActivity")
            Intent(this, ServerMainActivity::class.java)
        }

        startActivity(intent)
        finish()
    }

    private fun launchClient() {
        binding.progressAuth.visibility = View.VISIBLE

        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                viewModel.saveUserRole(UserRole.CLIENT)
                saveUserRoleToDatabase("client")
                binding.progressAuth.visibility = View.GONE
                Toast.makeText(this, "Запуск клиента", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, ClientMainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                binding.progressAuth.visibility = View.GONE
                Toast.makeText(this, "Ошибка входа: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }


    private fun showClientPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Доступ к геопозиции")
            .setMessage("Разрешение на определение местоположения необходимо для отображения серверов на карте.")
            .setPositiveButton("Продолжить") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Доступ к микрофону")
                    .setMessage("Разрешение на микрофон нужно для функции прослушивания сервера.")
                    .setPositiveButton("Разрешить") { _, _ ->
                        clientPermissionLauncher.launch(clientPermissionsToRequest)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }


}
