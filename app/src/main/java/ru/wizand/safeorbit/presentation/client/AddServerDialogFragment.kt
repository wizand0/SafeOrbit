package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import ru.wizand.safeorbit.R

class AddServerDialogFragment(
    private val existingIds: Collection<String>,
    private val onSave: (serverId: String, code: String, name: String) -> Unit
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_server, null)
        val nameField = view.findViewById<EditText>(R.id.editName)
        val serverIdField = view.findViewById<EditText>(R.id.editServerId)
        val codeField = view.findViewById<EditText>(R.id.editCode)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Добавить сервер")
            .setView(view)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val serverId = serverIdField.text.toString().trim()
                val code = codeField.text.toString().trim()
                val name = nameField.text.toString().trim()

                if (serverId.isBlank() || code.isBlank()) {
                    Toast.makeText(requireContext(), "ID и код обязательны", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (existingIds.contains(serverId)) {
                    Toast.makeText(requireContext(), "Сервер с таким ID уже добавлен", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                onSave(serverId, code, name)
                dialog.dismiss()
            }
        }

        return dialog
    }
}