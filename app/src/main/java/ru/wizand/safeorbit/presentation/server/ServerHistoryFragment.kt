package ru.wizand.safeorbit.presentation.server

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.safeorbit.R
import ru.wizand.safeorbit.presentation.server.ActivityLogAdapter
import ru.wizand.safeorbit.presentation.server.ActivityLogViewModel
import java.util.Calendar

class ServerHistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ActivityLogAdapter
    private lateinit var viewModel: ActivityLogViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_server_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewHistory)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val btnSelectDate = view.findViewById<Button>(R.id.btnSelectDate)
        val tvSelectedDate = view.findViewById<TextView>(R.id.tvSelectedDate)

        viewModel = ViewModelProvider(this)[ActivityLogViewModel::class.java]

        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                    viewModel.setFilterDate(selectedDate)
                    tvSelectedDate.text = selectedDate
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        // Долгое нажатие сбрасывает фильтр
        btnSelectDate.setOnLongClickListener {
            viewModel.setFilterDate(null)
            tvSelectedDate.text = "(все)"
            true
        }

        viewModel.filteredUiLogs.observe(viewLifecycleOwner) { logs ->
            if (!::adapter.isInitialized) {
                adapter = ActivityLogAdapter()
                recyclerView.adapter = adapter
            }
            adapter.submitList(logs)
        }
    }



}
