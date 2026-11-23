package com.example.taskflow.ui.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.ProjectDependencyEntity
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.data.repositories.buildPlanNodes
import com.example.taskflow.data.repositories.toPlanConfig
import com.example.taskflow.domain.GanttRange
import com.example.taskflow.domain.GanttRow
import com.example.taskflow.domain.GanttZoom
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.ProjectPlan
import com.example.taskflow.domain.buildGanttRows
import com.example.taskflow.domain.dependencyMap
import com.example.taskflow.domain.ganttRangeOf
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.planProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Estado completo que necesita la pantalla del Gantt
data class GanttState(
    val project: ProjectEntity,
    val plan: ProjectPlan,
    val rows: List<GanttRow>,
    val range: GanttRange,
    val deadline: Long?,
    val today: Long
)

@HiltViewModel
class ProjectGanttViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val projectId: Long = checkNotNull(savedStateHandle.get<Long>("projectId"))

    private val _zoom = MutableStateFlow(GanttZoom.WEEK)
    val zoom: StateFlow<GanttZoom> = _zoom.asStateFlow()

    private val _collapsed = MutableStateFlow<Set<Long>>(emptySet())
    val collapsed: StateFlow<Set<Long>> = _collapsed.asStateFlow()

    private val projectFlow = repository.observeProject(projectId)
    private val nodesFlow = repository.observeNodes(projectId)
    private val depsFlow = repository.observeDependencies(projectId)
    private val excludedFlow = repository.observeAllExcludedDays()

    val state: StateFlow<GanttState?> = combine(
        projectFlow, nodesFlow, depsFlow, excludedFlow, _collapsed
    ) { project, nodes, deps, excludedAll, collapsedSet ->
        if (project == null) {
            null
        } else {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            val excluded = excludedAll
                .filter { it.projectId == project.id }
                .map { it.epochDay }
                .toSet()
            val planNodes = buildPlanNodes(nodes, deps)
            val plan = planProject(project.toPlanConfig(excluded), planNodes, today)
            val depPairs = deps.map { Pair(it.predecessorId, it.successorId) }
            val rows = buildGanttRows(
                nodes,
                dependencyMap(depPairs),
                plan,
                project.deadlineDate,
                today,
                collapsedSet
            )
            GanttState(
                project = project,
                plan = plan,
                rows = rows,
                range = ganttRangeOf(plan, project.startDate, project.deadlineDate, today),
                deadline = project.deadlineDate,
                today = today
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setZoom(value: GanttZoom) {
        _zoom.value = value
    }

    fun toggleCollapsed(nodeId: Long) {
        val current = _collapsed.value
        _collapsed.value = if (current.contains(nodeId)) current - nodeId else current + nodeId
    }

    // Mueve la barra de una hoja a un dia nuevo: crea "no antes de"
    fun moveBar(nodeId: Long, newDay: Long, onResult: (OpResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.setNotBefore(nodeId, newDay))
        }
    }

    // Quita la restriccion "no antes de" de una hoja
    fun clearNotBefore(nodeId: Long, onResult: (OpResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.setNotBefore(nodeId, null))
        }
    }

    // Cambia la duracion de una hoja a minutos nuevos
    fun resizeBar(nodeId: Long, minutes: Int, onResult: (OpResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.setLeafMinutes(nodeId, minutes, System.currentTimeMillis()))
        }
    }

    // Anade una tarea a la raiz del proyecto
    fun addRootTask(title: String, onResult: (OpResult) -> Unit) {
        viewModelScope.launch {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            onResult(repository.addRootLeaf(projectId, title, System.currentTimeMillis(), today))
        }
    }

    // Datos de un nodo para abrir su dialogo de edicion
    suspend fun nodeById(nodeId: Long): com.example.taskflow.data.entities.ProjectNodeEntity? =
        repository.getNodes(projectId).firstOrNull { it.id == nodeId }

    // Guarda los cambios de un nodo desde el dialogo del Gantt
    suspend fun updateNodeFromGantt(
        nodeId: Long,
        draft: com.example.taskflow.domain.NodeDraft
    ): OpResult = repository.updateNode(nodeId, draft, System.currentTimeMillis())
}
