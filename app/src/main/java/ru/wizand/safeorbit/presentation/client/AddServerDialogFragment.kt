package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import ru.wizand.safeorbit.R

class AddServerDialogFragment(
    private val onSave: (serverId: String, code: String, name: String) -> Unit
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_server, null)
        val nameField = view.findViewById<EditText>(R.id.editName)
        val serverIdField = view.findViewById<EditText>(R.id.editServerId)
        val codeField = view.findViewById<EditText>(R.id.editCode)

        return AlertDialog.Builder(requireContext())
            .setTitle("Добавить сервер")
            .setView(view)
            .setPositiveButton("Сохранить") { _, _ ->
                val serverId = serverIdField.text.toString()
                val code = codeField.text.toString()
                val name = nameField.text.toString()
                onSave(serverId, code, name)
            }
            .setNegativeButton("Отмена", null)
            .create()
    }
}