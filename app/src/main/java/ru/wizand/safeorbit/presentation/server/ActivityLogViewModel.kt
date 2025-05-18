package ru.wizand.safeorbit.presentation.server

import android.app.Application
import android.provider.SyncStateContract.Helpers.update
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.wizand.safeorbit.data.ActivityLogEntity
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.model.ActivityLogUiModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ActivityLogViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "safeorbit-db"
    ).build()

    private val _uiLogs = MutableLiveData<List<ActivityLogUiModel>>()
    val uiLogs: LiveData<List<ActivityLogUiModel>> = _uiLogs

    init {
        cleanOldLogs()
        db.activityLogDao().getAllLogs().observeForever { logs ->
            _uiLogs.value = mergeWithMissingHours(logs)
        }
    }

    private fun mergeWithMissingHours(logs: List<ActivityLogEntity>): List<ActivityLogUiModel> {
        val groupedByDate = logs.groupBy { it.date }
        val result = mutableListOf<ActivityLogUiModel>()

        for ((date, dayLogs) in groupedByDate) {
            val hourlyMap = mutableMapOf<Int, ActivityLogEntity>()
            dayLogs.forEach { hourlyMap[it.startHour] = it }

            var hour = 0
            var currentEconomStart: Int? = null

            while (hour < 24) {
                val entity = hourlyMap[hour]
                if (entity != null) {
                    // Завершаем эконом-интервал, если был
                    if (currentEconomStart != null) {
                        result.add(
                            ActivityLogUiModel(
                                date, currentEconomStart, hour, "ЭКОНОМ"
                            )
                        )
                        currentEconomStart = null
                    }

                    result.add(
                        ActivityLogUiModel(
                            date,
                            entity.startHour,
                            entity.endHour,
                            entity.mode,
                            entity.steps,
                            entity.distanceMeters
                        )
                    )
                    hour = entity.endHour
                } else {
                    if (currentEconomStart == null) {
                        currentEconomStart = hour
                    }
                    hour++
                }
            }

            if (currentEconomStart != null && currentEconomStart < 24) {
                result.add(
                    ActivityLogUiModel(date, currentEconomStart, 24, "ЭКОНОМ")
                )
            }
        }

        return result.sortedWith(compareByDescending<ActivityLogUiModel> { it.date }
            .thenByDescending { it.startHour })
    }

    private fun cleanOldLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -30)
            }
            val cutoffDate = sdf.format(calendar.time)
            db.activityLogDao().deleteOlderThan(cutoffDate)
        }
    }

    private val _filterDate = MutableLiveData<String?>(null)
    fun setFilterDate(date: String?) {
        _filterDate.value = date
    }

    val filteredUiLogs: LiveData<List<ActivityLogUiModel>> = MediatorLiveData<List<ActivityLogUiModel>>().apply {
        addSource(uiLogs) { update() }
        addSource(_filterDate) { update() }
    }

    private fun MediatorLiveData<List<ActivityLogUiModel>>.update() {
        val allLogs = uiLogs.value.orEmpty()
        val dateFilter = _filterDate.value
        value = if (dateFilter == null) allLogs
        else allLogs.filter { it.date == dateFilter }
    }
}
