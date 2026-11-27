package com.example.taskflow.domain

import android.util.Log
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.daos.TaskReminderDao
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.data.repositories.SettingsRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

// Unico sitio donde se completa o reabre una tarea: crea la siguiente repeticion si es recurrente
// y propaga el cambio al nodo del proyecto si es una tarea materializada por un proyecto.
@Singleton
class TaskCompletionHelper @Inject constructor(
    private val taskDao: TaskDao,
    private val taskReminderDao: TaskReminderDao,
    private val settingsRepo: SettingsRepository,
    private val projectRepository: dagger.Lazy<ProjectRepository>
) {
    suspend fun complete(task: TaskEntity, nowMs: Long = System.currentTimeMillis()) {
        var spawned = task.recurrenceSpawned
        if (task.parentTaskId == null && !spawned && task.recurrenceType != "NONE" && task.projectKey == null) {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            val nextDate = nextSpawnDate(task, today)
            if (nextDate != null) {
                val newId = taskDao.insertTask(
                    task.copy(
                        id = 0L,
                        date = nextDate,
                        isCompleted = false,
                        completedAt = null,
                        originalDate = null,
                        deletedAt = null,
                        recurrenceSpawned = false,
                        createdAt = nowMs
                    )
                )
                val relative = taskReminderDao.getForTaskSync(task.id).filter { it.offsetMinutes != null }
                if (relative.isNotEmpty()) {
                    taskReminderDao.insertAll(relative.map { it.copy(id = 0L, taskId = newId) })
                }
                spawned = true
            }
        }
        taskDao.updateTask(task.copy(isCompleted = true, completedAt = nowMs, recurrenceSpawned = spawned))
        propagate(task, true, nowMs)
    }

    suspend fun uncomplete(task: TaskEntity) {
        taskDao.updateTask(task.copy(isCompleted = false, completedAt = null))
        propagate(task, false, System.currentTimeMillis())
    }

    private suspend fun propagate(task: TaskEntity, completed: Boolean, nowMs: Long) {
        if (task.projectKey == null) return
        try {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            projectRepository.get().onProjectTaskToggled(task, completed, nowMs, today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("TaskCompletionHelper", "project propagation failed", e)
        }
    }
}
