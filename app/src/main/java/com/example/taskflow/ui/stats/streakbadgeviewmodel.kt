package com.example.taskflow.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.TodayStateEvaluator
import com.example.taskflow.domain.currentStreak
import com.example.taskflow.domain.dayRecordsToStatuses
import com.example.taskflow.domain.logicalDateNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StreakBadgeViewModel @Inject constructor(
    private val recordDao: RecordDao,
    private val todayState: TodayStateEvaluator,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val streak: StateFlow<Int> = recordDao.getAllDayRecords().map { records ->
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        currentStreak(dayRecordsToStatuses(records), todayState.globalState(today))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
