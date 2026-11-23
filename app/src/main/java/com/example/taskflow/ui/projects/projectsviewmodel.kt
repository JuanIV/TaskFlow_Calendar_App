package com.example.taskflow.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.ProjectFormData
import com.example.taskflow.domain.logicalDateNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = repository.observeActiveProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<ProjectEntity>> = repository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Crea (existing == null) o actualiza un proyecto
    fun saveProject(existing: ProjectEntity?, form: ProjectFormData, onResult: (OpResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.saveProject(existing, form, System.currentTimeMillis()))
        }
    }

    // Duplica un proyecto (o crea uno desde plantilla con asTemplate = false). Devuelve el id nuevo o null.
    fun duplicate(sourceId: Long, newName: String, asTemplate: Boolean, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            onResult(repository.duplicateProject(sourceId, newName, asTemplate, today, System.currentTimeMillis()))
        }
    }

    fun softDelete(projectId: Long) {
        viewModelScope.launch { repository.softDeleteProject(projectId, System.currentTimeMillis()) }
    }

    fun restore(projectId: Long) {
        viewModelScope.launch { repository.restoreProject(projectId) }
    }
}
