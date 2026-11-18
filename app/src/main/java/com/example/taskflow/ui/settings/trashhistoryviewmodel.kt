package com.example.taskflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.EventRepository
import com.example.taskflow.data.repositories.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashHistoryViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val eventRepo: EventRepository
) : ViewModel() {

    val deletedTasks: StateFlow<List<TaskEntity>> = taskRepo.getDeletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedEvents: StateFlow<List<EventEntity>> = eventRepo.getDeletedEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedHistory: StateFlow<List<TaskEntity>> = taskRepo.getCompletedHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreTask(taskId: Long) {
        viewModelScope.launch { taskRepo.restoreTask(taskId) }
    }

    fun markPending(taskId: Long) {
        viewModelScope.launch { taskRepo.markPending(taskId) }
    }

    fun deleteTaskToTrash(taskId: Long) {
        viewModelScope.launch { taskRepo.softDeleteTask(taskId) }
    }

    fun deletePermanentlyTask(taskId: Long) {
        viewModelScope.launch { taskRepo.permanentlyDeleteTask(taskId) }
    }

    fun restoreEvent(eventId: Long) {
        viewModelScope.launch { eventRepo.restoreEvent(eventId) }
    }

    fun deletePermanentlyEvent(eventId: Long) {
        viewModelScope.launch { eventRepo.permanentlyDeleteEvent(eventId) }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            taskRepo.emptyTrash()
            eventRepo.emptyTrashEvents()
        }
    }
}
