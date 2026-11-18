package com.example.taskflow.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.data.repositories.TaskRepository
import com.example.taskflow.domain.logicalDateNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val repository: TaskRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = repository.getAllActiveTasks()
        .map { list -> list.filter { it.parentTaskId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val firstDay: StateFlow<DayOfWeek> = settingsRepo.firstDayFlow
        .map { DayOfWeek.of(it.coerceIn(1, 7)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayOfWeek.MONDAY)

    val todayEpoch: StateFlow<Long> = settingsRepo.dayStartHourFlow
        .map { logicalDateNow(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, logicalDateNow(0))

    // newDate = null deja la tarea sin fecha (y sin hora)
    fun move(task: TaskEntity, newDate: Long?) {
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    date = newDate,
                    dueMinuteOfDay = if (newDate == null) null else task.dueMinuteOfDay,
                    originalDate = if (newDate == null) task.originalDate else (task.originalDate ?: task.date)
                )
            )
        }
    }

    fun restore(old: TaskEntity) {
        viewModelScope.launch { repository.updateTask(old) }
    }
}
