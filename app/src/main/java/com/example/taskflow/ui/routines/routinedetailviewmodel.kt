package com.example.taskflow.ui.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskCompletionEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.data.repositories.RoutineRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.logicalDateNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long = checkNotNull(savedStateHandle.get<Long>("routineId"))

    private val _todayEpoch = MutableStateFlow(0L)
    val todayEpoch: StateFlow<Long> = _todayEpoch.asStateFlow()

    private val _dayStartHour = MutableStateFlow(0)
    val dayStartHour: StateFlow<Int> = _dayStartHour.asStateFlow()

    private val _routine = MutableStateFlow<RoutineEntity?>(null)
    val routine: StateFlow<RoutineEntity?> = _routine.asStateFlow()

    val tasks: StateFlow<List<RoutineTaskEntity>> = repository.getTasksForRoutine(routineId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _completionsToday = MutableStateFlow<List<RoutineTaskCompletionEntity>>(emptyList())
    val completionsToday: StateFlow<List<RoutineTaskCompletionEntity>> = _completionsToday.asStateFlow()

    init {
        viewModelScope.launch {
            val hour = settingsRepository.getDayStartHourSync()
            _dayStartHour.value = hour
            _todayEpoch.value = logicalDateNow(hour)
            repository.getAllRoutines().collect { list ->
                _routine.value = list.firstOrNull { it.id == routineId }
            }
        }
        viewModelScope.launch {
            _todayEpoch.filter { it > 0 }.collectLatest { epoch ->
                repository.getCompletions(routineId, epoch).collect { _completionsToday.value = it }
            }
        }
    }

    fun saveTask(existing: RoutineTaskEntity?, title: String, isOptional: Boolean, weekdaysMask: Int?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (existing == null) {
                val next = if (tasks.value.isEmpty()) 0 else tasks.value.maxOf { it.sortOrder } + 1
                repository.insertRoutineTask(
                    RoutineTaskEntity(
                        routineId = routineId,
                        title = trimmed,
                        sortOrder = next,
                        isOptional = isOptional,
                        weekdaysMask = weekdaysMask
                    )
                )
            } else {
                repository.updateRoutineTask(existing.copy(title = trimmed, isOptional = isOptional, weekdaysMask = weekdaysMask))
            }
        }
    }

    fun deleteTask(task: RoutineTaskEntity) {
        viewModelScope.launch {
            repository.updateRoutineTask(task.copy(deletedAtEpochDay = maxOf(1L, _todayEpoch.value)))
        }
    }

    fun restoreTask(task: RoutineTaskEntity) {
        viewModelScope.launch { repository.updateRoutineTask(task.copy(deletedAtEpochDay = null)) }
    }

    fun toggleTaskCompletion(taskId: Long, isCompletedNow: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId, _todayEpoch.value, System.currentTimeMillis(), !isCompletedNow)
        }
    }

    fun reorderTasks(reordered: List<RoutineTaskEntity>) {
        viewModelScope.launch { repository.updateTaskSortOrders(reordered) }
    }

    fun setPauseState(fromEpoch: Long?, untilEpoch: Long?) {
        viewModelScope.launch {
            val r = _routine.value ?: return@launch
            repository.updateRoutine(r.copy(pausedFromEpochDay = fromEpoch, pausedUntilEpochDay = untilEpoch))
        }
    }

    fun duplicate(asTemplate: Boolean, newName: String) {
        viewModelScope.launch { repository.duplicateRoutine(routineId, asTemplate, newName) }
    }
}
