package com.example.taskflow.ui.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.data.repositories.toPlanConfig
import com.example.taskflow.data.repositories.toPlanNode
import com.example.taskflow.domain.NodeDraft
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.ProjectFormData
import com.example.taskflow.domain.ProjectPlan
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _plan = MutableStateFlow<ProjectPlan?>(null)
    val plan: StateFlow<ProjectPlan?> = _plan.asStateFlow()

    private val _excludedDays = MutableStateFlow<List<Long>>(emptyList())
    val excludedDays: StateFlow<List<Long>> = _excludedDays.asStateFlow()

    private val _today = MutableStateFlow(0L)
    val today: StateFlow<Long> = _today.asStateFlow()

    private val _pullCandidate = MutableStateFlow<Long?>(null)
    val pullCandidate: StateFlow<Long?> = _pullCandidate.asStateFlow()

    private fun now(): Long = System.currentTimeMillis()

    private suspend fun refreshAll(current: ProjectEntity?, currentNodes: List<ProjectNodeEntity>) {
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        val excluded = repository.getExcludedDays(projectId)
        _today.value = today
        _excludedDays.value = excluded
        _plan.value = repository.loadPlan(projectId, today)
        _pullCandidate.value = if (current == null) {
            null
        } else {
            findPullCandidate(
                current.toPlanConfig(excluded.toSet()),
                currentNodes.map { it.toPlanNode() },
                today
            )
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

    // Cada cambio del proyecto o de sus nodos recalcula el plan. Debe ir al final de la clase.
    init {
        viewModelScope.launch {
            combine(project, nodes) { p, n -> Pair(p, n) }.collect { pair ->
                refreshAll(pair.first, pair.second)
            }
        }
    }
}
