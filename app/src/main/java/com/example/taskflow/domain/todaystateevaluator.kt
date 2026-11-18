package com.example.taskflow.domain

import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.RoutineEntity
import javax.inject.Inject
import javax.inject.Singleton

// Estado "en vivo" de hoy: COMPLETED, PENDING o NEUTRAL (nada planificado).
@Singleton
class TodayStateEvaluator @Inject constructor(
    private val routineDao: RoutineDao,
    private val taskDao: TaskDao
) {
    suspend fun routineState(routine: RoutineEntity, today: Long): String {
        if (!routineAppliesOn(routine, today)) return "NEUTRAL"
        val tasks = routineDao.getTasksForRoutineSync(routine.id).filter { taskAppliesOn(it, today) }
        val mandatory = tasks.filter { !it.isOptional }
        val required = if (mandatory.isEmpty()) tasks else mandatory
        if (required.isEmpty()) return "NEUTRAL"
        val done = routineDao.getAllCompletionsOnDateSync(today).map { it.routineTaskId }.toSet()
        return if (required.all { it.id in done }) "COMPLETED" else "PENDING"
    }

    suspend fun globalState(today: Long): String {
        val states = routineDao.getActiveRoutinesSync().map { routineState(it, today) }
        val tasks = taskDao.getTasksForDateSync(today).filter { it.parentTaskId == null }
        val anyPending = states.any { it == "PENDING" } || tasks.any { !it.isCompleted }
        val anyPlanned = states.any { it == "COMPLETED" } || tasks.isNotEmpty()
        return when {
            anyPending -> "PENDING"
            anyPlanned -> "COMPLETED"
            else -> "NEUTRAL"
        }
    }
}
