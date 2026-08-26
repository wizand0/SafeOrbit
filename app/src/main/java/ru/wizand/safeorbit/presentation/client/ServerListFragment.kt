package ru.wizand.safeorbit.presentation.client

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ru.wizand.safeorbit.data.ServerEntity
import ru.wizand.safeorbit.data.firebase.FirebaseRepository
import ru.wizand.safeorbit.databinding.FragmentServerListBinding
import ru.wizand.safeorbit.presentation.client.commands.CommandViewModel

class ServerListFragment : Fragment() {
    private val mapViewModel: ServerMapViewModel by activityViewModels()
    private val commandViewModel: CommandViewModel by activityViewModels()
    private val viewModel: ClientViewModel by activityViewModels()
    private var _binding: FragmentServerListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ServerAdapter
    private val firebaseRepository by lazy { FirebaseRepository(requireContext().applicationContext) }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { raw ->
            val parts = raw.split("|", limit = 2)
            if (parts.size == 2) tryAddServer(parts[0].trim(), parts[1].trim(), parts[0].trim())
            else Toast.makeText(requireContext(), "РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ QR-РєРѕРґ", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentServerListBinding.inflate(inflater, container, false)
        binding.serverRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ServerAdapter(emptyList(), { showDetails(it) }, { showEditDialog(it) }, { deleteServer(it) })
        binding.serverRecyclerView.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = deleteServer(adapter.currentItems[vh.adapterPosition])
        }).attachToRecyclerView(binding.serverRecyclerView)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.fabAddInList.setOnClickListener {
            AddServerDialogFragment(viewModel.serverNameMap.value?.keys.orEmpty()) { id, token, enteredName ->
                tryAddServer(id, token, if (enteredName.isBlank()) id else enteredName)
            }.show(parentFragmentManager, "AddServerDialog")
        }
        binding.fabScanQr?.setOnClickListener { qrScannerLauncher.launch(ScanOptions().apply { setPrompt("РЎРєР°РЅРёСЂСѓР№С‚Рµ QR-РєРѕРґ pairing"); setBeepEnabled(true) }) }
        observeData()
    }

    private fun tryAddServer(serverId: String, token: String, name: String) {
        if (viewModel.serverNameMap.value?.containsKey(serverId) == true) {
            Toast.makeText(requireContext(), "РЎРµСЂРІРµСЂ СѓР¶Рµ РґРѕР±Р°РІР»РµРЅ", Toast.LENGTH_SHORT).show(); return
        }
        firebaseRepository.pairClientToServer(serverId, token) { success ->
            requireActivity().runOnUiThread {
                if (success) {
                    viewModel.addServer(serverId, "", name)
                    Toast.makeText(requireContext(), "РЎРµСЂРІРµСЂ РґРѕР±Р°РІР»РµРЅ", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(requireContext(), "РќРµРґРµР№СЃС‚РІРёС‚РµР»СЊРЅС‹Р№ РёР»Рё РїСЂРѕСЃСЂРѕС‡РµРЅРЅС‹Р№ С‚РѕРєРµРЅ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeData() {
        viewModel.serverNameMap.observe(viewLifecycleOwner) { updateList(it, viewModel.iconUriMap.value.orEmpty()) }
        viewModel.iconUriMap.observe(viewLifecycleOwner) { updateList(viewModel.serverNameMap.value.orEmpty(), it) }
        viewModel.loadAndObserveServers()
    }

    private fun updateList(names: Map<String, String>, icons: Map<String, String?>) {
        adapter.updateData(names.map { (id, name) -> ServerEntity(0, id, "", name, icons[id]) })
    }

    private fun showEditDialog(server: ServerEntity) {
        val input = EditText(requireContext()).apply { setText(server.name) }
        AlertDialog.Builder(requireContext()).setTitle("РР·РјРµРЅРёС‚СЊ РёРјСЏ").setView(input)
            .setPositiveButton("РЎРѕС…СЂР°РЅРёС‚СЊ") { _, _ -> viewModel.renameServer(server.serverId, input.text.toString().trim()) }
            .setNegativeButton("РћС‚РјРµРЅР°", null).show()
    }
    private fun deleteServer(server: ServerEntity) { AlertDialog.Builder(requireContext()).setTitle("РЈРґР°Р»РёС‚СЊ СЃРµСЂРІРµСЂ?").setPositiveButton("РЈРґР°Р»РёС‚СЊ") { _, _ -> viewModel.deleteServer(server.serverId) }.setNegativeButton("РћС‚РјРµРЅР°", null).show() }
    private fun showDetails(server: ServerEntity) { commandViewModel.requestLocationUpdate(server.serverId) }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

