package com.example.taskflow.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.daos.FocusLogDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.FocusState
import com.example.taskflow.focus.FocusManager
import com.example.taskflow.focus.PomodoroConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val manager: FocusManager,
    private val settingsRepo: SettingsRepository,
    private val taskDao: TaskDao,
    private val focusLogDao: FocusLogDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val initialTaskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L

    val state: StateFlow<FocusState> = manager.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, FocusState())

    val tasks: StateFlow<List<TaskEntity>> = taskDao.getAllActiveTasks()
        .map { list -> list.filter { it.parentTaskId == null && !it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<PomodoroConfig> = combine(
        settingsRepo.pomodoroFocusFlow,
        settingsRepo.pomodoroShortFlow,
        settingsRepo.pomodoroLongFlow,
        settingsRepo.pomodoroCyclesFlow
    ) { focus, short, long, cycles -> PomodoroConfig(focus, short, long, cycles) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PomodoroConfig(25, 5, 15, 4))

    // Se recalcula cada vez que cambia el estado (al parar o cambiar de fase)
    val todayMinutes: StateFlow<Int> = state.map {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        focusLogDao.focusMinutesBetween(startOfDay, startOfDay + 24L * 60 * 60 * 1000)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun start(mode: String, taskId: Long?, timerMinutes: Int) {
        viewModelScope.launch { manager.start(mode, taskId, timerMinutes) }
    }

    fun stop() {
        viewModelScope.launch { manager.stop() }
    }

    fun skip() {
        viewModelScope.launch { manager.skip() }
    }

    fun saveConfig(focus: Int, short: Int, long: Int, cycles: Int) {
        viewModelScope.launch {
            settingsRepo.setPomodoro(focus.coerceIn(1, 180), short.coerceIn(1, 60), long.coerceIn(1, 120), cycles.coerceIn(1, 12))
        }
    }
}
