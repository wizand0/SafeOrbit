package ru.wizand.safeorbit.presentation.client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.role.RoleSelectionActivity

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        view.findViewById<Button>(R.id.btnResetRole).setOnClickListener {
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("user_role").apply()
            prefs.edit().remove("permissions_intro_shown").apply()
            startActivity(Intent(requireContext(), RoleSelectionActivity::class.java))
            requireActivity().finish()
        }
        return view
    }
}