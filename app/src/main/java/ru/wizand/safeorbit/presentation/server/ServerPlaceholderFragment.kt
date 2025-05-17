package ru.wizand.safeorbit.presentation.server

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import ru.wizand.safeorbit.R

class ServerPlaceholderFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_server_placeholder, container, false)
    }
}
