package com.example.taskflow.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.backup.BackupManager
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.scheduling.SchedulingCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val coordinator: SchedulingCoordinator,
    private val backupManager: BackupManager
) : ViewModel() {

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val dayStartHour = repo.dayStartHourFlow.asState(0)
    val overdueMode = repo.overdueModeFlow.asState("SHOW")
    val todayTasksMinute = repo.todayTasksMinuteFlow.asState(7 * 60)
    val morningEnabled = repo.morningEnabledFlow.asState(true)
    val morningMinute = repo.morningMinuteFlow.asState(8 * 60)
    val eveningEnabled = repo.eveningEnabledFlow.asState(true)
    val eveningMinute = repo.eveningMinuteFlow.asState(21 * 60 + 30)
    val quietEnabled = repo.quietEnabledFlow.asState(false)
    val quietStart = repo.quietStartFlow.asState(23 * 60)
    val quietEnd = repo.quietEndFlow.asState(7 * 60)
    val looseEnabled = repo.looseEnabledFlow.asState(true)
    val looseInterval = repo.looseIntervalFlow.asState(6)
    val looseStart = repo.looseStartFlow.asState(9 * 60)
    val looseEnd = repo.looseEndFlow.asState(21 * 60)

    private fun apply(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            coordinator.rescheduleAll()
            coordinator.refreshNotifications()
        }
    }

    fun updateDayStartHour(hour: Int) = apply { repo.setDayStartHour(hour) }
    fun updateOverdueMode(mode: String) = apply { repo.setOverdueMode(mode) }
    fun updateTodayTasksMinute(minute: Int) = apply { repo.setTodayTasksMinute(minute) }
    fun updateMorning(enabled: Boolean, minute: Int) = apply { repo.setMorningSummary(enabled, minute) }
    fun updateEvening(enabled: Boolean, minute: Int) = apply { repo.setEveningSummary(enabled, minute) }
    fun updateQuiet(enabled: Boolean, start: Int, end: Int) = apply { repo.setQuietHours(enabled, start, end) }
    fun updateLoose(enabled: Boolean, interval: Int, start: Int, end: Int) = apply { repo.setLooseReminder(enabled, interval, start, end) }

    fun exportData(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(backupManager.exportDatabase(uri)) }
    }

    fun importData(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(backupManager.importDatabase(uri)) }
    }
}
