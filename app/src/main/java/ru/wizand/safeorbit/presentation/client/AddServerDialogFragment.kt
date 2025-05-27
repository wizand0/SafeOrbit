package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.DialogAddServerBinding

class AddServerDialogFragment(
    private val existingIds: Collection<String>,
    private val onSave: (serverId: String, code: String, name: String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAddServerBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_server))
            .setView(binding.root)
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val serverId = binding.editServerId.text.toString().trim()
                val code = binding.editCode.text.toString().trim()
                val name = binding.editName.text.toString().trim()

                if (serverId.isBlank() || code.isBlank()) {
                    Toast.makeText(requireContext(),
                        getString(R.string.id_code_are_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (existingIds.contains(serverId)) {
                    Toast.makeText(requireContext(),
                        getString(R.string.such_ad_is_saved), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                onSave(serverId, code, name)
                dialog.dismiss()
            }
        }

        return dialog
    }
}
