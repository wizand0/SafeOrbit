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

        val now = Calendar.getInstance()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
        val currentHour = now.get(Calendar.HOUR_OF_DAY)

        for ((date, dayLogs) in groupedByDate) {
            val isToday = date == currentDate
            val hourLimit = if (isToday) currentHour + 1 else 24
            val totalStepsForDay = dayLogs.sumOf { it.steps ?: 0 }

            // 👇 Добавляем сводку по шагам
            result.add(
                ActivityLogUiModel(
                    date = date,
                    dailySteps = totalStepsForDay,
                    isSummary = true
                )
            )

            val fullMap = mutableMapOf<Int, ActivityLogEntity>()
            dayLogs.forEach { fullMap[it.startHour] = it }

            var hour = 0
            var pendingEconomStart: Int? = null

            while (hour < hourLimit) {
                val entity = fullMap[hour]

                if (entity != null || dayLogs.any { it.startHour <= hour && it.endHour > hour && it.mode == "Активность" }) {
                    if (pendingEconomStart != null) {
                        result.add(
                            ActivityLogUiModel(
                                date = date,
                                startHour = pendingEconomStart,
                                endHour = hour,
                                mode = "ЭКОНОМ"
                            )
                        )
                        pendingEconomStart = null
                    }

                    val actualEntity = entity ?: dayLogs.find {
                        it.startHour <= hour && it.endHour > hour && it.mode == "Активность"
                    }

                    result.add(
                        ActivityLogUiModel(
                            date = date,
                            startHour = hour,
                            endHour = hour + 1,
                            mode = "Активность",
                            steps = actualEntity?.steps,
                            distanceMeters = actualEntity?.distanceMeters
                        )
                    )
                } else {
                    if (pendingEconomStart == null) {
                        pendingEconomStart = hour
                    }
                }

                hour++
            }

            if (pendingEconomStart != null && pendingEconomStart < hourLimit) {
                result.add(
                    ActivityLogUiModel(
                        date = date,
                        startHour = pendingEconomStart,
                        endHour = hourLimit,
                        mode = "ЭКОНОМ"
                    )
                )
            }
        }

//        return result.sortedWith(compareByDescending<ActivityLogUiModel> { it.date }
//            .thenByDescending { it.isSummary.not() }
//            .thenByDescending { it.startHour })

        return result.sortedWith(compareByDescending<ActivityLogUiModel> { it.date }
                .thenByDescending { it.isSummary } // ✅ summary=true => выше
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
