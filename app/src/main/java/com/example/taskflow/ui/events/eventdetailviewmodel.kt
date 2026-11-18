package com.example.taskflow.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventReminderEntity
import com.example.taskflow.data.repositories.EventRepository
import com.example.taskflow.domain.EventOccurrence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {

    private val _occurrenceState = MutableStateFlow<EventOccurrence?>(null)
    val occurrenceState: StateFlow<EventOccurrence?> = _occurrenceState.asStateFlow()

    private val _baseEvent = MutableStateFlow<EventEntity?>(null)
    val baseEvent: StateFlow<EventEntity?> = _baseEvent.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _reminders = MutableStateFlow<List<EventReminderEntity>>(emptyList())
    val reminders: StateFlow<List<EventReminderEntity>> = _reminders.asStateFlow()

    private var loadedKey: Pair<Long, Long>? = null
    private var jobs: List<Job> = emptyList()

    fun loadOccurrence(eventId: Long, originalDate: Long) {
        if (loadedKey == Pair(eventId, originalDate)) return
        loadedKey = Pair(eventId, originalDate)
        jobs.forEach { it.cancel() }
        _isLoading.value = true

        val occurrenceJob = viewModelScope.launch {
            repository.getExpandedEvents(originalDate - 400, originalDate + 400).collect { list ->
                _baseEvent.value = repository.getEventById(eventId)
                _occurrenceState.value = list.firstOrNull { it.eventId == eventId && it.originalDate == originalDate }
                _isLoading.value = false
            }
        }
        val remindersJob = viewModelScope.launch {
            repository.getRemindersForEvent(eventId).collect { _reminders.value = it }
        }
        jobs = listOf(occurrenceJob, remindersJob)
    }
}
