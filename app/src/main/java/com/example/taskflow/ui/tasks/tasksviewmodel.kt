package com.example.taskflow.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.daos.FocusLogDao
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.entities.TaskReminderEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.data.repositories.TaskRepository
import com.example.taskflow.domain.logicalDateNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepo: SettingsRepository,
    private val focusLogDao: FocusLogDao
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val allActiveTasks: StateFlow<List<TaskEntity>> = repository.getAllActiveTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEpoch: StateFlow<Long> = combine(settingsRepo.dayStartHourFlow, ticker) { hour, _ -> logicalDateNow(hour) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, logicalDateNow(0))

    val sortMode: StateFlow<String> = settingsRepo.taskSortModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "MANUAL")

    fun setSortMode(mode: String) {
        viewModelScope.launch { settingsRepo.setTaskSortMode(mode) }
    }

    suspend fun loadReminders(taskId: Long): List<TaskReminderEntity> = repository.getTaskReminders(taskId)

    suspend fun focusMinutes(taskId: Long): Int = focusLogDao.totalMinutesForTask(taskId)

    fun saveTaskFromResult(result: TaskFormResult, parentTaskId: Long? = null) {
        viewModelScope.launch {
            val existing = if (result.id != 0L) repository.getTaskById(result.id) else null
            val now = System.currentTimeMillis()
            val dueMinute = if (result.date != null) result.dueMinuteOfDay else null
            val toSave = existing?.copy(
                title = result.title,
                date = result.date,
                priority = result.priority,
                notes = result.notes,
                dueMinuteOfDay = dueMinute,
                estimatedMinutes = result.estimatedMinutes,
                recurrenceType = result.recurrenceType,
                recurrenceInterval = result.recurrenceInterval,
                recurrenceWeekdaysMask = result.recurrenceWeekdaysMask,
                recurrenceSpawned = if (existing.recurrenceType != result.recurrenceType && !existing.isCompleted) false else existing.recurrenceSpawned
            ) ?: TaskEntity(
                title = result.title,
                date = result.date,
                createdAt = now,
                priority = result.priority,
                dueMinuteOfDay = dueMinute,
                notes = result.notes,
                parentTaskId = parentTaskId,
                estimatedMinutes = result.estimatedMinutes,
                recurrenceType = result.recurrenceType,
                recurrenceInterval = result.recurrenceInterval,
                recurrenceWeekdaysMask = result.recurrenceWeekdaysMask
            )
            val savedId = repository.saveTask(toSave)
            result.newSubtasks.forEach { subTitle ->
                repository.saveTask(
                    TaskEntity(
                        title = subTitle.trim(),
                        date = result.date,
                        createdAt = now,
                        parentTaskId = savedId
                    )
                )
            }
            repository.replaceTaskReminders(savedId, result.reminders)
        }
    }

    fun addSubtask(parent: TaskEntity, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.saveTask(
                TaskEntity(
                    title = trimmed,
                    date = parent.date,
                    createdAt = System.currentTimeMillis(),
                    parentTaskId = parent.id
                )
            )
        }
    }

    fun postponeTask(task: TaskEntity, newEpochDay: Long) {
        viewModelScope.launch {
            repository.updateTask(task.copy(date = newEpochDay, originalDate = task.originalDate ?: task.date))
        }
    }

    // Deshacer un movimiento: vuelve a escribir la tarea tal y como estaba
    fun restoreTaskState(oldTask: TaskEntity) {
        viewModelScope.launch { repository.updateTask(oldTask) }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch { repository.setCompleted(task, !task.isCompleted) }
    }

    fun completeWithSubtasks(task: TaskEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.getSubtasksSync(task.id).filter { !it.isCompleted }.forEach {
                repository.updateTask(it.copy(isCompleted = true, completedAt = now))
            }
            repository.setCompleted(task, true)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { repository.softDeleteTask(taskId) }
    }

    fun restoreTask(taskId: Long) {
        viewModelScope.launch { repository.restoreTask(taskId) }
    }

    fun reorderTasks(tasks: List<TaskEntity>) {
        viewModelScope.launch { repository.updateSortOrders(tasks) }
    }
}
