package ru.wizand.safeorbit.presentation.server

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import ru.wizand.safeorbit.databinding.FragmentServerHistoryBinding
import java.util.Calendar

class ServerHistoryFragment : Fragment() {

    private var _binding: FragmentServerHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ActivityLogAdapter
    private lateinit var viewModel: ActivityLogViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServerHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())

        viewModel = ViewModelProvider(this)[ActivityLogViewModel::class.java]

        binding.btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                    viewModel.setFilterDate(selectedDate)
                    binding.tvSelectedDate.text = selectedDate
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        binding.btnSelectDate.setOnLongClickListener {
            viewModel.setFilterDate(null)
            binding.tvSelectedDate.text = "(все)"
            true
        }

        viewModel.filteredUiLogs.observe(viewLifecycleOwner) { logs ->
            if (!::adapter.isInitialized) {
                adapter = ActivityLogAdapter()
                binding.recyclerViewHistory.adapter = adapter
            }
            adapter.submitList(logs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
