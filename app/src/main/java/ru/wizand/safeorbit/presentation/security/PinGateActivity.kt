package ru.wizand.safeorbit.presentation.security

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.data.security.EncryptedPreferencesManager
import ru.wizand.safeorbit.databinding.ActivityPinGateBinding
import ru.wizand.safeorbit.presentation.server.ServerMainActivity

/**
 * Экран создания и проверки PIN-кода для доступа к функциям сервера.
 * Использует EncryptedPreferencesManager для безопасного хранения.
 */
class PinGateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinGateBinding
    private lateinit var encryptedPrefs: EncryptedPreferencesManager

    private var savedPin: String? = null
    private var failedAttempts = 0
    private var isPinVisible = false

    // Режим подтверждения (при создании нового PIN)
    private var isConfirmationMode = false
    private var firstPinEntry: String? = null

    companion object {
        private const val TAG = "PinGateActivity"
        private const val MAX_ATTEMPTS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinGateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация менеджера зашифрованных данных
        encryptedPrefs = EncryptedPreferencesManager(this)

        // Получаем сохранённый PIN из защищённого хранилища
        savedPin = encryptedPrefs.getPin()

        setupUI()
        setupPinVisibilityToggle()
    }

    /**
     * Настройка интерфейса в зависимости от наличия PIN
     */
    private fun setupUI() {
        if (savedPin == null) {
            // Режим создания нового PIN
            binding.tvPinTitle.text = "Создайте PIN-код"
            binding.tvPinDescription.text = "Введите 4-6 цифр для защиты доступа к серверу"
            binding.btnVerifyPin.text = "Далее"
            binding.etPinVerify.hint = "Введите PIN"
        } else {
            // Режим проверки существующего PIN
            binding.tvPinTitle.text = "Введите PIN-код"
            binding.tvPinDescription.text = "Для доступа к функциям сервера"
            binding.btnVerifyPin.text = "Подтвердить"
            binding.etPinVerify.hint = "PIN-код"
        }

        binding.btnVerifyPin.setOnClickListener {
            handlePinInput()
        }

        // Кнопка сброса PIN (только если PIN уже установлен)
        if (savedPin != null) {
            binding.btnResetPin?.setOnClickListener {
                showResetPinDialog()
            }
        }
    }

    /**
     * Переключатель видимости PIN
     */
    private fun setupPinVisibilityToggle() {
        binding.btnTogglePinVisibility?.setOnClickListener {
            isPinVisible = !isPinVisible

            if (isPinVisible) {
                binding.etPinVerify.inputType = InputType.TYPE_CLASS_NUMBER
                binding.btnTogglePinVisibility.setIconResource(R.drawable.ic_visibility_off)
            } else {
                binding.etPinVerify.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                binding.btnTogglePinVisibility.setIconResource(R.drawable.ic_visibility)
            }

            // Перемещаем курсор в конец
            binding.etPinVerify.setSelection(binding.etPinVerify.text?.length ?: 0)
        }
    }

    /**
     * Обработка ввода PIN
     */
    private fun handlePinInput() {
        val input = binding.etPinVerify.text.toString().trim()

        // Валидация длины
        if (input.length < 4) {
            Toast.makeText(this, "PIN должен содержать минимум 4 цифры", Toast.LENGTH_SHORT).show()
            return
        }

        if (input.length > 6) {
            Toast.makeText(this, "PIN не может быть длиннее 6 цифр", Toast.LENGTH_SHORT).show()
            return
        }

        // Валидация формата (только цифры)
        if (!input.all { it.isDigit() }) {
            Toast.makeText(this, "PIN должен содержать только цифры", Toast.LENGTH_SHORT).show()
            return
        }

        if (savedPin == null) {
            // Создание нового PIN
            handleNewPinCreation(input)
        } else {
            // Проверка существующего PIN
            handlePinVerification(input)
        }
    }

    /**
     * Обработка создания нового PIN с подтверждением
     */
    private fun handleNewPinCreation(input: String) {
        if (!isConfirmationMode) {
            // Первый ввод PIN
            firstPinEntry = input
            isConfirmationMode = true

            binding.tvPinTitle.text = "Подтвердите PIN-код"
            binding.tvPinDescription.text = "Введите PIN ещё раз"
            binding.btnVerifyPin.text = "Создать"
            binding.etPinVerify.text?.clear()
            binding.etPinVerify.hint = "Повторите PIN"

            Toast.makeText(this, "Теперь введите PIN ещё раз", Toast.LENGTH_SHORT).show()
        } else {
            // Подтверждение PIN
            if (input == firstPinEntry) {
                // PIN совпадает, сохраняем
                encryptedPrefs.savePin(input)
                encryptedPrefs.setPinVerified(true)

                Toast.makeText(this, "✅ PIN успешно создан", Toast.LENGTH_SHORT).show()
                navigateToServerMain()
            } else {
                // PIN не совпадает, начинаем заново
                Toast.makeText(this, "❌ PIN не совпадает, попробуйте снова", Toast.LENGTH_LONG).show()
                resetPinCreation()
            }
        }
    }

    /**
     * Сброс процесса создания PIN
     */
    private fun resetPinCreation() {
        isConfirmationMode = false
        firstPinEntry = null

        binding.tvPinTitle.text = "Создайте PIN-код"
        binding.tvPinDescription.text = "Введите 4-6 цифр для защиты доступа к серверу"
        binding.btnVerifyPin.text = "Далее"
        binding.etPinVerify.text?.clear()
        binding.etPinVerify.hint = "Введите PIN"
    }

    /**
     * Проверка существующего PIN
     */
    private fun handlePinVerification(input: String) {
        if (input == savedPin) {
            // PIN верен
            encryptedPrefs.setPinVerified(true)
            failedAttempts = 0

            Toast.makeText(this, "✅ Доступ разрешён", Toast.LENGTH_SHORT).show()
            navigateToServerMain()
        } else {
            // PIN неверен
            failedAttempts++

            val attemptsLeft = MAX_ATTEMPTS - failedAttempts

            if (attemptsLeft > 0) {
                binding.etPinVerify.text?.clear()
                Toast.makeText(
                    this,
                    "❌ Неверный PIN. Осталось попыток: $attemptsLeft",
                    Toast.LENGTH_LONG
                ).show()

                // Тряска поля ввода для визуального feedback
                binding.etPinVerify.animate()
                    .translationX(-10f)
                    .setDuration(50)
                    .withEndAction {
                        binding.etPinVerify.animate()
                            .translationX(10f)
                            .setDuration(50)
                            .withEndAction {
                                binding.etPinVerify.animate()
                                    .translationX(0f)
                                    .setDuration(50)
                                    .start()
                            }
                            .start()
                    }
                    .start()
            } else {
                // Превышен лимит попыток
                showMaxAttemptsDialog()
            }
        }
    }

    /**
     * Диалог при превышении лимита попыток
     */
    private fun showMaxAttemptsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Превышен лимит попыток")
            .setMessage("Вы ввели неверный PIN $MAX_ATTEMPTS раз. Необходимо сбросить PIN через настройки.")
            .setPositiveButton("Сбросить PIN") { _, _ ->
                showResetPinDialog()
            }
            .setNegativeButton("Выход") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Диалог подтверждения сброса PIN
     */
    private fun showResetPinDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Сброс PIN-кода")
            .setMessage(
                """
                Вы уверены, что хотите сбросить PIN?
                
                Это также сбросит все данные сервера:
                • Server ID
                • Код подключения
                • Настройки
                """.trimIndent()
            )
            .setPositiveButton("Да, сбросить") { _, _ ->
                resetAllData()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Полный сброс данных
     */
    private fun resetAllData() {
        encryptedPrefs.clearAll()

        Toast.makeText(this, "Данные сброшены", Toast.LENGTH_SHORT).show()

        // Возврат к экрану выбора роли
        val intent = Intent(this, ru.wizand.safeorbit.presentation.role.RoleSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("fromReset", true)
        startActivity(intent)
        finish()
    }

    /**
     * Переход к главному экрану сервера
     */
    private fun navigateToServerMain() {
        startActivity(Intent(this, ServerMainActivity::class.java))
        finish()
    }

    /**
     * Блокировка кнопки "Назад" для предотвращения обхода проверки PIN
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        MaterialAlertDialogBuilder(this)
            .setTitle("Выход")
            .setMessage("Для доступа к функциям сервера необходимо ввести PIN-код. Выйти из приложения?")
            .setPositiveButton("Да") { _, _ ->
                finishAffinity() // Полностью закрыть приложение
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}