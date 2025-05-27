package ru.wizand.safeorbit.presentation.server

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.databinding.FragmentServerPlaceholderBinding

class ServerPlaceholderFragment : Fragment() {

    private var _binding: FragmentServerPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServerPlaceholderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.container.animate()
            .alpha(1f)
            .setDuration(500)
            .start()

        binding.btnEmail.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:makandrei@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.safeorbit_your_questions))
            }
            startActivity(Intent.createChooser(emailIntent, getString(R.string.choose_mail_app)))
        }

        binding.btnGitHub.setOnClickListener {
            val githubIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wizand0"))
            startActivity(githubIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
