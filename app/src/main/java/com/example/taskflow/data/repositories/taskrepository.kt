package com.example.taskflow.data.repositories

import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.daos.TaskReminderDao
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.entities.TaskReminderEntity
import com.example.taskflow.domain.TaskCompletionHelper
import com.example.taskflow.scheduling.SchedulingCoordinator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val taskReminderDao: TaskReminderDao,
    private val completionHelper: TaskCompletionHelper,
    private val coordinator: SchedulingCoordinator
) {
    fun getLooseTasks(): Flow<List<TaskEntity>> = taskDao.getLooseTasks()
    fun getTasksForDate(targetDate: Long): Flow<List<TaskEntity>> = taskDao.getTasksForDate(targetDate)
    fun getAllActiveTasks(): Flow<List<TaskEntity>> = taskDao.getAllActiveTasks()
    fun getSubtasks(parentId: Long): Flow<List<TaskEntity>> = taskDao.getSubtasksForTask(parentId)
    fun getDeletedTasks(): Flow<List<TaskEntity>> = taskDao.getDeletedTasks()
    fun getCompletedHistory(): Flow<List<TaskEntity>> = taskDao.getCompletedHistory()

    suspend fun getTaskById(taskId: Long): TaskEntity? = taskDao.getTaskById(taskId)
    suspend fun getTasksForDateSync(targetDate: Long): List<TaskEntity> = taskDao.getTasksForDateSync(targetDate)
    suspend fun getTasksUpToDateSync(maxDate: Long): List<TaskEntity> = taskDao.getTasksUpToDateSync(maxDate)
    suspend fun getSubtasksSync(parentId: Long): List<TaskEntity> = taskDao.getSubtasksForTaskSync(parentId)

    suspend fun saveTask(task: TaskEntity): Long {
        return if (task.id == 0L) {
            val id = taskDao.insertTask(task)
            coordinator.onDataChanged()
            id
        } else {
            taskDao.updateTask(task)
            coordinator.onDataChanged()
            task.id
        }
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
        coordinator.onDataChanged()
    }

    suspend fun softDeleteTask(taskId: Long) {
        taskDao.softDeleteTaskAndSubtasks(taskId, System.currentTimeMillis())
        coordinator.onDataChanged()
    }

    suspend fun restoreTask(taskId: Long) {
        taskDao.restoreTask(taskId)
        coordinator.onDataChanged()
    }

    suspend fun markPending(taskId: Long) {
        taskDao.markPending(taskId)
        coordinator.onDataChanged()
    }

    suspend fun permanentlyDeleteTask(taskId: Long) {
        taskDao.permanentlyDeleteTask(taskId)
        coordinator.onDataChanged()
    }

    suspend fun emptyTrash() {
        taskDao.emptyTrash()
        coordinator.onDataChanged()
    }

    suspend fun updateSortOrders(tasks: List<TaskEntity>) {
        tasks.forEachIndexed { index, task ->
            taskDao.updateTask(task.copy(sortOrder = index))
        }
        coordinator.onDataChanged()
    }

    suspend fun getTaskReminders(taskId: Long): List<TaskReminderEntity> = taskReminderDao.getForTaskSync(taskId)

    suspend fun replaceTaskReminders(taskId: Long, reminders: List<TaskReminderEntity>) {
        taskReminderDao.replaceForTask(taskId, reminders)
        coordinator.onDataChanged()
    }

    suspend fun setCompleted(task: TaskEntity, completed: Boolean) {
        if (completed) completionHelper.complete(task) else completionHelper.uncomplete(task)
        coordinator.onDataChanged()
    }
}
