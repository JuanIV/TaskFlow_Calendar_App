package com.example.taskflow.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.data.repositories.toPlanConfig
import com.example.taskflow.data.repositories.toPlanNode
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.ProjectFormData
import com.example.taskflow.domain.ProjectSummary
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.summarizeProject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Un proyecto del listado con su resumen calculado
data class ProjectCard(val project: ProjectEntity, val summary: ProjectSummary)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val today: StateFlow<Long> = settingsRepo.dayStartHourFlow
        .map { logicalDateNow(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, logicalDateNow(0))

    val templates: StateFlow<List<ProjectEntity>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cards: StateFlow<List<ProjectCard>> = combine(
        repository.observeActiveProjects(),
        repository.observeAllNodes(),
        repository.observeAllExcludedDays(),
        today
    ) { projects, nodes, excluded, day ->
        val nodesByProject = nodes.groupBy { it.projectId }
        val excludedByProject = excluded
            .groupBy { it.projectId }
            .mapValues { entry -> entry.value.map { it.epochDay }.toSet() }
        projects.map { project ->
            val projectNodes = nodesByProject[project.id].orEmpty()
            val summary = summarizeProject(
                projectId = project.id,
                config = project.toPlanConfig(excludedByProject[project.id].orEmpty()),
                nodes = projectNodes.map { it.toPlanNode() },
                titles = projectNodes.associate { it.id to it.title },
                today = day
            )
            ProjectCard(project, summary)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Crea (existing == null) o actualiza un proyecto
    fun saveProject(existing: ProjectEntity?, form: ProjectFormData, onResult: (OpResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.saveProject(existing, form, System.currentTimeMillis()))
        }
    }

    // Crea una copia de un proyecto o de una plantilla. Devuelve el id nuevo o null.
    fun duplicate(sourceId: Long, newName: String, asTemplate: Boolean, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.duplicateProject(sourceId, newName, asTemplate, today.value, System.currentTimeMillis()))
        }
    }

    fun softDelete(projectId: Long) {
        viewModelScope.launch { repository.softDeleteProject(projectId, System.currentTimeMillis()) }
    }
}
