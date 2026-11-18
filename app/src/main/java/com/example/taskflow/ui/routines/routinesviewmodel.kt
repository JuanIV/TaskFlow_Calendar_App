package com.example.taskflow.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.entities.RoutineDayRecordEntity
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskCompletionEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.data.repositories.RoutineRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.currentStreak
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.routineAppliesOn
import com.example.taskflow.domain.routineRecordsToStatuses
import com.example.taskflow.domain.taskAppliesOn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineCardInfo(
    val routine: RoutineEntity,
    val appliesToday: Boolean,
    val done: Int,
    val required: Int,
    val streak: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val routineDao: RoutineDao,
    private val recordDao: RecordDao,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val dayStartHour: StateFlow<Int> = settingsRepo.dayStartHourFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val todayEpoch: StateFlow<Long> = combine(settingsRepo.dayStartHourFlow, ticker) { hour, _ -> logicalDateNow(hour) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, logicalDateNow(0))

    val routines: StateFlow<List<RoutineEntity>> = repository.getAllRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<RoutineEntity>> = repository.getTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cards: StateFlow<List<RoutineCardInfo>> = todayEpoch.flatMapLatest { today ->
        combine(
            repository.getAllRoutines(),
            routineDao.observeAllTasks(),
            routineDao.observeCompletionsOnDate(today),
            recordDao.observeAllRoutineRecords()
        ) { routineList, tasks, completions, records ->
            routineList.map { buildCard(it, tasks, completions, records, today) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildCard(
        routine: RoutineEntity,
        tasks: List<RoutineTaskEntity>,
        completions: List<RoutineTaskCompletionEntity>,
        records: List<RoutineDayRecordEntity>,
        today: Long
    ): RoutineCardInfo {
        val applies = routineAppliesOn(routine, today)
        val mine = tasks.filter { it.routineId == routine.id && taskAppliesOn(it, today) }
        val mandatory = mine.filter { !it.isOptional }
        val required = if (mandatory.isEmpty()) mine else mandatory
        val doneIds = completions.map { it.routineTaskId }.toSet()
        val done = required.count { it.id in doneIds }
        val liveState = when {
            !applies || required.isEmpty() -> "NEUTRAL"
            done == required.size -> "COMPLETED"
            else -> "PENDING"
        }
        val streak = currentStreak(routineRecordsToStatuses(records.filter { it.routineId == routine.id }), liveState)
        return RoutineCardInfo(routine, applies, done, required.size, streak)
    }

    fun saveRoutine(routine: RoutineEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (routine.id == 0L) repository.insertRoutine(routine) else repository.updateRoutine(routine)
            onComplete()
        }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch { repository.deleteRoutine(routineId) }
    }

    fun duplicateRoutine(routineId: Long, asTemplate: Boolean, newName: String) {
        viewModelScope.launch { repository.duplicateRoutine(routineId, asTemplate, newName) }
    }
}
