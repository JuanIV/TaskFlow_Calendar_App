package com.example.taskflow.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.DayRecordEntity
import com.example.taskflow.data.entities.RoutineDayRecordEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.TodayStateEvaluator
import com.example.taskflow.domain.bestStreak
import com.example.taskflow.domain.compliancePercent
import com.example.taskflow.domain.currentStreak
import com.example.taskflow.domain.dayRecordsToStatuses
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.routineRecordsToStatuses
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// routineId = -1 es la tarjeta global
data class StatCard(
    val routineId: Long,
    val name: String?,
    val colorArgb: Int?,
    val current: Int,
    val best: Int,
    val percent30: Int
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val routineDao: RoutineDao,
    private val taskDao: TaskDao,
    private val todayState: TodayStateEvaluator,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val changes: Flow<Unit> = merge(
        recordDao.getAllDayRecords().map { },
        recordDao.observeAllRoutineRecords().map { },
        routineDao.getAllRoutines().map { },
        routineDao.observeAllTasks().map { },
        routineDao.observeAllCompletions().map { },
        taskDao.getAllActiveTasks().map { }
    )

    val cards: StateFlow<List<StatCard>> = changes
        .map { compute() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun compute(): List<StatCard> {
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        val result = mutableListOf<StatCard>()

        val globalStatuses = dayRecordsToStatuses(recordDao.getAllDayRecordsSync())
        val globalLive = todayState.globalState(today)
        result.add(
            StatCard(
                -1L, null, null,
                currentStreak(globalStatuses, globalLive),
                bestStreak(globalStatuses, globalLive),
                compliancePercent(globalStatuses, today - 30, today - 1).toInt()
            )
        )

        for (routine in routineDao.getActiveRoutinesSync()) {
            val end = routine.endDate
            if (end != null && end < today) continue
            val statuses = routineRecordsToStatuses(recordDao.getRecordsForRoutineSync(routine.id))
            val live = todayState.routineState(routine, today)
            result.add(
                StatCard(
                    routine.id, routine.name, routine.colorArgb,
                    currentStreak(statuses, live),
                    bestStreak(statuses, live),
                    compliancePercent(statuses, today - 30, today - 1).toInt()
                )
            )
        }
        return result
    }

    // Solo se usa en compilaciones de depuracion
    fun seedSampleData() {
        viewModelScope.launch {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            val routines = routineDao.getActiveRoutinesSync()
            val options = listOf("COMPLETED", "COMPLETED", "COMPLETED", "FAILED", "GRACE", "PAUSED")
            for (i in 60 downTo 1) {
                val day = today - i
                var anyFailed = false
                for (routine in routines) {
                    val status = options.random()
                    val required = if (status == "PAUSED") 0 else 3
                    val done = when (status) {
                        "COMPLETED" -> 3
                        "FAILED", "GRACE" -> 1
                        else -> 0
                    }
                    recordDao.insertRoutineDayRecord(RoutineDayRecordEntity(routine.id, day, status, required, done))
                    if (status == "FAILED") anyFailed = true
                }
                recordDao.insertDayRecord(DayRecordEntity(day, if (anyFailed) "FAILED" else "COMPLETED", 3, if (anyFailed) 2 else 3))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            recordDao.clearDayRecords()
            recordDao.clearRoutineRecords()
        }
    }
}
