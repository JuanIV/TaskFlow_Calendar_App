package com.example.taskflow.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventExceptionEntity
import com.example.taskflow.data.entities.EventReminderEntity
import com.example.taskflow.data.repositories.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {

    suspend fun loadReminderOffsets(eventId: Long): List<Int> =
        repository.getRemindersSync(eventId).map { it.offsetMinutes }

    private fun toEntities(offsets: List<Int>): List<EventReminderEntity> =
        offsets.distinct().map { EventReminderEntity(eventId = 0L, offsetMinutes = it) }

    fun saveEvent(event: EventEntity, offsets: List<Int>) {
        viewModelScope.launch { repository.saveEvent(event, toEntities(offsets)) }
    }

    fun saveException(exception: EventExceptionEntity) {
        viewModelScope.launch { repository.saveException(exception) }
    }

    fun updateSeries(base: EventEntity, referenceDate: Long, updated: EventEntity, offsets: List<Int>) {
        viewModelScope.launch { repository.updateEventSeriesFromOccurrence(base, referenceDate, updated, toEntities(offsets)) }
    }

    fun splitSeries(original: EventEntity, splitDate: Long, newEvent: EventEntity, offsets: List<Int>) {
        viewModelScope.launch { repository.splitEventSeries(original, splitDate, newEvent, toEntities(offsets)) }
    }

    fun softDelete(eventId: Long) {
        viewModelScope.launch { repository.softDeleteEvent(eventId) }
    }

    fun restore(eventId: Long) {
        viewModelScope.launch { repository.restoreEvent(eventId) }
    }

    fun truncate(eventId: Long, untilEpoch: Long) {
        viewModelScope.launch { repository.truncateSeries(eventId, untilEpoch) }
    }

    fun restoreRow(event: EventEntity) {
        viewModelScope.launch { repository.restoreEventRow(event) }
    }

    fun deleteOccurrence(eventId: Long, originalDate: Long) {
        viewModelScope.launch { repository.deleteOccurrence(eventId, originalDate) }
    }

    fun undeleteOccurrence(eventId: Long, originalDate: Long) {
        viewModelScope.launch { repository.undeleteOccurrence(eventId, originalDate) }
    }
}
