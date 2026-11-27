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
import com.example.taskflow.data.repositories.toPlanNode
import com.example.taskflow.domain.NodeDraft
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.ProjectFormData
import com.example.taskflow.domain.ProjectPlan
import com.example.taskflow.domain.GridDays
import com.example.taskflow.domain.GridRow
import com.example.taskflow.domain.WeekCell
import com.example.taskflow.domain.buildGridRows
import com.example.taskflow.domain.gridDaysOf
import com.example.taskflow.domain.weekCellsOf
import com.example.taskflow.domain.ProjectKind
import com.example.taskflow.domain.effectiveProjectKind
import com.example.taskflow.domain.GridCell
import com.example.taskflow.domain.assignmentsToLeafPlanned
import com.example.taskflow.domain.isAssignableLeaf
import com.example.taskflow.domain.childrenOfNode
import com.example.taskflow.domain.findPullCandidate
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.moveTargetsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.taskflow.domain.GRID_EXTEND_DAYS
import com.example.taskflow.domain.MAX_GRID_DAYS
import com.example.taskflow.domain.extendedGridDays
import com.example.taskflow.domain.initialGridDays
import com.example.taskflow.domain.logicalDayOfMillis
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val projectId: Long = checkNotNull(savedStateHandle.get<Long>("projectId"))

    val project: StateFlow<ProjectEntity?> = repository.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nodes: StateFlow<List<ProjectNodeEntity>> = repository.observeNodes(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dependencies: StateFlow<List<ProjectDependencyEntity>> = repository.observeDependencies(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _plan = MutableStateFlow<ProjectPlan?>(null)
    val plan: StateFlow<ProjectPlan?> = _plan.asStateFlow()

    private val _excludedDays = MutableStateFlow<List<Long>>(emptyList())
    val excludedDays: StateFlow<List<Long>> = _excludedDays.asStateFlow()

    private val _today = MutableStateFlow(0L)
    val today: StateFlow<Long> = _today.asStateFlow()

    private val _pullCandidate = MutableStateFlow<Long?>(null)
    val pullCandidate: StateFlow<Long?> = _pullCandidate.asStateFlow()

    private val _deviation = MutableStateFlow<com.example.taskflow.domain.DeviationSummary?>(null)
    val deviation: StateFlow<com.example.taskflow.domain.DeviationSummary?> = _deviation.asStateFlow()

    private val _gridRows = MutableStateFlow<List<GridRow>>(emptyList())
    val gridRows: StateFlow<List<GridRow>> = _gridRows.asStateFlow()

    private val _gridDays = MutableStateFlow<GridDays?>(null)
    val gridDays: StateFlow<GridDays?> = _gridDays.asStateFlow()

    private var gridExtraBack = 0
    private var gridExtraForward = 0
    private var gridBase: GridDays? = null
    private var gridFirstDay: DayOfWeek = DayOfWeek.MONDAY
    private var gridCompletionDays: Map<Long, Long> = emptyMap()
    private var gridCompletedCells: Set<Pair<Long, Long>> = emptySet()

    fun extendGridForward() {
        val base = gridBase ?: return
        if (base.size + gridExtraBack + gridExtraForward >= MAX_GRID_DAYS) return
        gridExtraForward += GRID_EXTEND_DAYS
        applyGridRange()
    }

    fun extendGridBackward() {
        val base = gridBase ?: return
        if (base.size + gridExtraBack + gridExtraForward >= MAX_GRID_DAYS) return
        gridExtraBack += GRID_EXTEND_DAYS
        applyGridRange()
    }

    private fun applyGridRange() {
        val base = gridBase ?: return
        _gridDays.value = extendedGridDays(base, gridExtraBack, gridExtraForward)
        if (_editMode.value) {
            recomputePendingRows()
        } else {
            viewModelScope.launch { refreshAll(project.value, nodes.value, dependencies.value) }
        }
    }

    private val _weekView = MutableStateFlow(false)
    val weekView: StateFlow<Boolean> = _weekView.asStateFlow()

    private val _collapsedGrid = MutableStateFlow<Set<Long>>(emptySet())
    val collapsedGrid: StateFlow<Set<Long>> = _collapsedGrid.asStateFlow()

    fun isAdvanced(): Boolean = effectiveProjectKind(project.value?.kind) == ProjectKind.ADVANCED

    fun toggleWeekView() {
        _weekView.value = !_weekView.value
    }

    fun toggleCollapsedGrid(nodeId: Long) {
        val current = _collapsedGrid.value
        _collapsedGrid.value = if (current.contains(nodeId)) current - nodeId else current + nodeId
        if (_editMode.value) recomputePendingRows()
    }

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _pendingCells = MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())

    // Entra en modo edicion: copia las asignaciones persistidas como punto de partida editable.
    fun enterGridEditMode() {
        viewModelScope.launch {
            val current = repository.getAssignments(projectId).map { Pair(it.nodeId, it.epochDay) }.toSet()
            _pendingCells.value = current
            _weekView.value = false
            _editMode.value = true
            recomputePendingRows()
        }
    }

    // Pinta o despinta una celda. Solo tiene efecto sobre hojas (no grupos ni hitos).
    fun toggleGridCell(nodeId: Long, epochDay: Long) {
        if (!_editMode.value) return
        if (!isAssignableLeaf(nodes.value, nodeId)) return
        val key = Pair(nodeId, epochDay)
        val current = _pendingCells.value
        _pendingCells.value = if (current.contains(key)) current - key else current + key
        recomputePendingRows()
    }

    // Descarta los cambios y vuelve a la vista persistida.
    fun cancelGridEdit() {
        _editMode.value = false
        _pendingCells.value = emptySet()
        viewModelScope.launch { refreshAll(project.value, nodes.value, dependencies.value) }
    }

    // Guarda las celdas pintadas y sale del modo edicion.
    fun saveGridEdit(onResult: (OpResult) -> Unit) {
        viewModelScope.launch {
            val result = repository.replaceAssignments(projectId, _pendingCells.value)
            if (result == OpResult.OK) {
                _editMode.value = false
                _pendingCells.value = emptySet()
                refreshAll(project.value, nodes.value, dependencies.value)
            }
            onResult(result)
        }
    }

    // Recalcula la rejilla mostrada a partir de las celdas pendientes, sin tocar lo persistido.
    private fun recomputePendingRows() {
        val days = _gridDays.value ?: return
        val leafPlanned = assignmentsToLeafPlanned(_pendingCells.value)
        val leafCompleted = nodes.value.filter { it.completed }.map { it.id }.toSet()
        _gridRows.value = buildGridRows(
            nodes.value, days, leafPlanned, leafCompleted, _collapsedGrid.value,
            gridCompletionDays, gridCompletedCells
        )
    }

    val firstDayOfWeekValue: StateFlow<Int> = settingsRepo.firstDayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private fun now(): Long = System.currentTimeMillis()

    private suspend fun refreshAll(
        current: ProjectEntity?,
        currentNodes: List<ProjectNodeEntity>,
        currentDeps: List<ProjectDependencyEntity>
    ) {
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        val excluded = repository.getExcludedDays(projectId)
        _today.value = today
        _excludedDays.value = excluded
        _plan.value = repository.loadPlan(projectId, today)
        _pullCandidate.value = if (current == null || effectiveProjectKind(current.kind) == ProjectKind.ADVANCED) {
            null
        } else {
            findPullCandidate(
                current.toPlanConfig(excluded.toSet()),
                buildPlanNodes(currentNodes, currentDeps),
                today
            )
        }
        _deviation.value = com.example.taskflow.domain.deviationSummary(currentNodes)
        if (effectiveProjectKind(current?.kind) == ProjectKind.ADVANCED) {
            val p = current!!
            val assignments = repository.getAssignments(projectId)
            val leafPlanned = leafPlannedFromAssignments(assignments)
            val leafCompleted = currentNodes.filter { it.completed }.map { it.id }.toSet()
            val dayStart = settingsRepo.getDayStartHourSync()
            val zone = ZoneId.systemDefault()
            gridFirstDay = DayOfWeek.of(settingsRepo.firstDayFlow.first().coerceIn(1, 7))
            gridCompletionDays = currentNodes
                .filter { it.completed }
                .mapNotNull { n -> n.completedAt?.let { Pair(n.id, logicalDayOfMillis(it, dayStart, zone)) } }
                .toMap()
            gridCompletedCells = repository.getCompletedCellTasks(projectId)
            val base = initialGridDays(p.startDate, assignments.map { it.epochDay }, p.deadlineDate, today, gridFirstDay)
            gridBase = base
            val days = extendedGridDays(base, gridExtraBack, gridExtraForward)
            _gridDays.value = days
            if (_editMode.value) {
                recomputePendingRows()
            } else {
                _gridRows.value = buildGridRows(
                    currentNodes, days, leafPlanned, leafCompleted, _collapsedGrid.value,
                    gridCompletionDays, gridCompletedCells
                )
            }
        } else {
            _gridDays.value = null
            _gridRows.value = emptyList()
        }
    }

    private fun launchResult(onResult: (OpResult) -> Unit, block: suspend () -> OpResult) {
        viewModelScope.launch { onResult(block()) }
    }

    fun saveProject(form: ProjectFormData, onResult: (OpResult) -> Unit) = launchResult(onResult) {
        val current = project.value ?: return@launchResult OpResult.NOT_FOUND
        repository.saveProject(current, form, now())
    }

    fun setStatus(newStatus: String, onResult: (OpResult) -> Unit) = launchResult(onResult) {
        repository.setStatus(projectId, newStatus)
    }

    // Duplica este proyecto. Devuelve el id nuevo o null.
    fun duplicate(newName: String, asTemplate: Boolean, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.duplicateProject(projectId, newName, asTemplate, _today.value, now()))
        }
    }

    fun softDelete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.softDeleteProject(projectId, now())
            onDone()
        }
    }

    fun addNode(parentId: Long?, draft: NodeDraft, onResult: (OpResult) -> Unit) = launchResult(onResult) {
        repository.addNode(projectId, parentId, draft, now())
    }

    fun updateNode(nodeId: Long, draft: NodeDraft, onResult: (OpResult) -> Unit) = launchResult(onResult) {
        repository.updateNode(nodeId, draft, now())
    }

    fun deleteNode(nodeId: Long, confirmed: Boolean, onResult: (OpResult) -> Unit) = launchResult(onResult) {
        repository.deleteNode(nodeId, confirmed)
    }

    fun moveNode(nodeId: Long, newParentId: Long?, newIndex: Int, onResult: (OpResult) -> Unit) = launchResult(onResult) {
        repository.moveNode(nodeId, newParentId, newIndex)
    }

    // Sube (delta = -1) o baja (delta = 1) un nodo entre sus hermanos
    fun moveSibling(nodeId: Long, delta: Int, onResult: (OpResult) -> Unit) {
        val all = nodes.value
        val node = all.firstOrNull { it.id == nodeId }
        if (node == null) {
            onResult(OpResult.NOT_FOUND)
            return
        }
        val siblings = childrenOfNode(all, node.parentId)
        val index = siblings.indexOfFirst { it.id == nodeId }
        val target = index + delta
        if (index < 0 || target < 0 || target >= siblings.size) {
            onResult(OpResult.OK)
            return
        }
        moveNode(nodeId, node.parentId, target, onResult)
    }

    fun setNodeCompleted(nodeId: Long, completed: Boolean, actualMinutes: Int?, onResult: (OpResult) -> Unit) =
        launchResult(onResult) {
            repository.setNodeCompleted(nodeId, completed, actualMinutes, now())
        }

    // Destinos validos para mover un nodo (la raiz se ofrece desde la UI)
    fun moveTargets(nodeId: Long): List<ProjectNodeEntity> = moveTargetsFor(nodes.value, nodeId)

    // Adelanta una tarea a hoy (RF-19)
    fun pullForward(nodeId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.setPulledForward(nodeId, _today.value)
            onDone()
        }
    }

    // Anade o quita una dependencia: successorId espera a predecessorId
    fun setDependency(predecessorId: Long, successorId: Long, enabled: Boolean, onResult: (OpResult) -> Unit) =
        launchResult(onResult) {
            repository.setDependency(projectId, predecessorId, successorId, enabled)
        }

    // Filas para exportar a CSV. Se llama desde la pantalla al pulsar Exportar.
    suspend fun exportRows(): List<List<String?>> = repository.exportRows(projectId, _today.value)

    // Asignaciones manuales de un proyecto avanzado: nodeId -> (dia -> minutos).
    // Cada dia asignado cuenta 60 minutos; la rejilla solo necesita saber si hay trabajo.
    private fun leafPlannedFromAssignments(
        assignments: List<com.example.taskflow.data.entities.ProjectCellAssignmentEntity>
    ): Map<Long, Map<Long, Int>> {
        val out = HashMap<Long, MutableMap<Long, Int>>()
        for (a in assignments) {
            out.getOrPut(a.nodeId) { HashMap() }[a.epochDay] = 60
        }
        return out
    }

    // Cada cambio del proyecto, de sus nodos o de sus dependencias recalcula el plan.
    init {
        viewModelScope.launch {
            combine(project, nodes, dependencies, repository.observeProjectTasks(projectId)) { p, n, d, _ -> Triple(p, n, d) }.collect { t ->
                refreshAll(t.first, t.second, t.third)
            }
        }
    }
}
