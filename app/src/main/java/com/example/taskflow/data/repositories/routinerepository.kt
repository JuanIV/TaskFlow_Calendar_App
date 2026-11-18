package com.example.taskflow.data.repositories

import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskCompletionEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.scheduling.SchedulingCoordinator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val coordinator: SchedulingCoordinator
) {
    fun getAllRoutines(): Flow<List<RoutineEntity>> = routineDao.getAllRoutines()
    fun getTemplates(): Flow<List<RoutineEntity>> = routineDao.getTemplates()
    fun getTasksForRoutine(routineId: Long): Flow<List<RoutineTaskEntity>> = routineDao.getTasksForRoutine(routineId)
    fun getCompletions(routineId: Long, date: Long): Flow<List<RoutineTaskCompletionEntity>> = routineDao.getCompletionsForRoutineOnDate(routineId, date)

    suspend fun getRoutineById(routineId: Long): RoutineEntity? = routineDao.getRoutineById(routineId)

    suspend fun insertRoutine(routine: RoutineEntity): Long {
        val id = routineDao.insertRoutine(routine)
        coordinator.onDataChanged()
        return id
    }

    suspend fun updateRoutine(routine: RoutineEntity) {
        routineDao.updateRoutine(routine)
        coordinator.onDataChanged()
    }

    suspend fun deleteRoutine(routineId: Long) {
        routineDao.deleteRoutine(routineId)
        coordinator.onDataChanged()
    }

    suspend fun insertRoutineTask(task: RoutineTaskEntity) {
        routineDao.insertRoutineTask(task)
        coordinator.onDataChanged()
    }

    suspend fun updateRoutineTask(task: RoutineTaskEntity) {
        routineDao.updateRoutineTask(task)
        coordinator.onDataChanged()
    }

    suspend fun toggleTaskCompletion(routineTaskId: Long, date: Long, completedAt: Long, isCompleted: Boolean) {
        if (isCompleted) {
            routineDao.insertTaskCompletion(RoutineTaskCompletionEntity(routineTaskId, date, completedAt))
        } else {
            routineDao.deleteTaskCompletion(routineTaskId, date)
        }
        coordinator.onDataChanged()
    }

    suspend fun updateTaskSortOrders(tasks: List<RoutineTaskEntity>) {
        tasks.forEachIndexed { index, task ->
            routineDao.updateRoutineTask(task.copy(sortOrder = index))
        }
        coordinator.onDataChanged()
    }

    suspend fun duplicateRoutine(routineId: Long, asTemplate: Boolean, newName: String) {
        val original = routineDao.getRoutineById(routineId) ?: return
        val tasks = routineDao.getTasksForRoutineSync(routineId)
        val newRoutine = original.copy(
            id = 0,
            name = newName,
            isTemplate = asTemplate,
            createdAt = System.currentTimeMillis()
        )
        val newId = routineDao.insertRoutine(newRoutine)
        tasks.forEach { task ->
            routineDao.insertRoutineTask(task.copy(id = 0, routineId = newId))
        }
        coordinator.onDataChanged()
    }
}
