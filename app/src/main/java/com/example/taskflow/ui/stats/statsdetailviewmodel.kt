package com.example.taskflow.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.DayStatus
import com.example.taskflow.domain.TodayStateEvaluator
import com.example.taskflow.domain.bestStreak
import com.example.taskflow.domain.compliancePercent
import com.example.taskflow.domain.currentStreak
import com.example.taskflow.domain.logicalDateNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DayInfo(val status: String, val required: Int, val done: Int)

data class StatsDetailState(
    val loaded: Boolean = false,
    val routineName: String? = null,
    val colorArgb: Int? = null,
    val today: Long = 0L,
    val days: Map<Long, DayInfo> = emptyMap(),
    val current: Int = 0,
    val best: Int = 0
)

@HiltViewModel
class StatsDetailViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val routineDao: RoutineDao,
    private val todayState: TodayStateEvaluator,
    private val settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<Long>("routineId") ?: -1L

    private val recordsFlow: Flow<List<Pair<Long, DayInfo>>> =
        if (routineId == -1L) {
            recordDao.getAllDayRecords().map { list ->
                list.map { Pair(it.date, DayInfo(it.status, it.requiredCount, it.doneCount)) }
            }
        } else {
            recordDao.getRecordsForRoutine(routineId).map { list ->
                list.map { Pair(it.date, DayInfo(it.status, it.requiredCount, it.doneCount)) }
            }
        }

    val state: StateFlow<StatsDetailState> = recordsFlow.map { entries ->
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        val routine = if (routineId == -1L) null else routineDao.getRoutineById(routineId)
        val live = when {
            routineId == -1L -> todayState.globalState(today)
            routine != null -> todayState.routineState(routine, today)
            else -> "NEUTRAL"
        }
        val statuses = entries.map { DayStatus(it.first, it.second.status) }
        StatsDetailState(
            loaded = true,
            routineName = routine?.name,
            colorArgb = routine?.colorArgb,
            today = today,
            days = entries.toMap(),
            current = currentStreak(statuses, live),
            best = bestStreak(statuses, live)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsDetailState())
}
