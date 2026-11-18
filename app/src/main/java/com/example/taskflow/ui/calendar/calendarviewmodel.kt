package com.example.taskflow.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.repositories.EventRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.logicalDateNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: EventRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val calendarView: StateFlow<String> = settingsRepo.calendarViewFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "MONTH")

    val firstDay: StateFlow<DayOfWeek> = settingsRepo.firstDayFlow
        .map { DayOfWeek.of(it.coerceIn(1, 7)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayOfWeek.MONDAY)

    val todayEpoch: StateFlow<Long> = combine(settingsRepo.dayStartHourFlow, ticker) { hour, _ -> logicalDateNow(hour) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, logicalDateNow(0))

    fun setCalendarView(view: String) {
        viewModelScope.launch { settingsRepo.setCalendarView(view) }
    }

    fun occurrencesFor(fromEpoch: Long, toEpoch: Long): Flow<List<EventOccurrence>> =
        repository.getExpandedEvents(fromEpoch, toEpoch)
}
