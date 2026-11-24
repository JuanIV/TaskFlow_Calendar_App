package com.example.taskflow.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.ProjectTodayItem
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.projectTodayItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectTodayViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val settingsRepo: SettingsRepository,
    private val focusManager: com.example.taskflow.focus.FocusManager
) : ViewModel() {

    private val _items = MutableStateFlow<List<ProjectTodayItem>>(emptyList())
    val items: StateFlow<List<ProjectTodayItem>> = _items.asStateFlow()

    // Recalcula las partes de hoy de todos los proyectos activos
    fun refresh() {
        viewModelScope.launch {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            val result = ArrayList<ProjectTodayItem>()
            for ((project, plan) in repository.getActiveProjectPlansSync(today)) {
                val titles = repository.getNodeTitles(project.id)
                result.addAll(projectTodayItems(project.id, project.name, plan, titles, today))
            }
            _items.value = result
        }
    }

    // Completa la parte indicada y recalcula
    fun completePart(item: ProjectTodayItem) {
        viewModelScope.launch {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            repository.completeTodayPart(item.nodeId, item.minutes, System.currentTimeMillis(), today)
            refresh()
        }
    }

    // Arranca un enfoque de 25 minutos ligado a la hoja de hoy (fase 2e)
    fun startFocus(item: ProjectTodayItem) {
        viewModelScope.launch {
            val minutes = settingsRepo.pomodoroFocusFlow.first()
            focusManager.startForProjectNode(item.nodeId, minutes)
        }
    }

    init {
        // Sin observar cambios de la base de datos en este paquete: se refresca al entrar en pantalla
        refresh()
    }
}
