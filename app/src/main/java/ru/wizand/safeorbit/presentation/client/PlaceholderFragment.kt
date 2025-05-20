package ru.wizand.safeorbit.presentation.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import ru.wizand.safeorbit.BuildConfig
import ru.wizand.safeorbit.R

class PlaceholderFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_placeholder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<LinearLayout>(R.id.container)?.animate()
            ?.alpha(1f)
            ?.setDuration(500)
            ?.start()

        view.findViewById<Button>(R.id.btnEmail)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:makandrei@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "SafeOrbit — вопрос/предложение")
            }
            startActivity(Intent.createChooser(intent, "Выберите почтовое приложение"))
        }

        view.findViewById<Button>(R.id.btnGitHub)?.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wizand0")))
        }


    }
}
