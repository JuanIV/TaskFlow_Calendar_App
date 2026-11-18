package com.example.taskflow.domain

import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.daos.TaskReminderDao
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

// Unico sitio donde se completa una tarea: crea la siguiente repeticion si es recurrente.
@Singleton
class TaskCompletionHelper @Inject constructor(
    private val taskDao: TaskDao,
    private val taskReminderDao: TaskReminderDao,
    private val settingsRepo: SettingsRepository
) {
    suspend fun complete(task: TaskEntity, nowMs: Long = System.currentTimeMillis()) {
        var spawned = task.recurrenceSpawned
        if (task.parentTaskId == null && !spawned && task.recurrenceType != "NONE") {
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
    }

    suspend fun uncomplete(task: TaskEntity) {
        taskDao.updateTask(task.copy(isCompleted = false, completedAt = null))
    }
}
