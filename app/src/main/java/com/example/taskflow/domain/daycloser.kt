package com.example.taskflow.domain

import androidx.room.withTransaction
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.db.AppDatabase
import com.example.taskflow.data.entities.DayRecordEntity
import com.example.taskflow.data.entities.RoutineDayRecordEntity
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.data.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class RoutineDayResult(val routineId: Long, val status: String, val required: Int, val done: Int)
data class GlobalDayResult(val status: String, val required: Int, val done: Int)

fun evaluateRoutineDay(
    routine: RoutineEntity,
    tasks: List<RoutineTaskEntity>,
    completedTaskIds: Set<Long>,
    day: Long,
    graceUsedInMonth: Int
): RoutineDayResult? {
    if (!routineWouldApplyIgnoringPause(routine, day)) return null
    if (isRoutinePausedOn(routine, day)) return RoutineDayResult(routine.id, "PAUSED", 0, 0)

    val applicable = tasks.filter { it.deletedAtEpochDay == null && taskAppliesOn(it, day) }
    val mandatory = applicable.filter { !it.isOptional }
    val required = if (mandatory.isEmpty()) applicable else mandatory
    if (required.isEmpty()) return null

    val done = required.count { it.id in completedTaskIds }
    var status = if (done == required.size) "COMPLETED" else "FAILED"
    if (status == "FAILED" && graceUsedInMonth < routine.graceDaysPerMonth) status = "GRACE"
    return RoutineDayResult(routine.id, status, required.size, done)
}

fun evaluateGlobalDay(
    routineResults: List<RoutineDayResult>,
    tasksRequired: Int,
    tasksDone: Int
): GlobalDayResult {
    val counted = routineResults.filter { it.status != "PAUSED" }
    val required = counted.sumOf { it.required } + tasksRequired
    val done = counted.sumOf { it.done } + tasksDone
    val anyFailed = routineResults.any { it.status == "FAILED" } || tasksDone < tasksRequired
    val nothing = routineResults.all { it.status == "PAUSED" } && tasksRequired == 0
    val status = when {
        anyFailed -> "FAILED"
        nothing -> "NEUTRAL"
        else -> "COMPLETED"
    }
    return GlobalDayResult(status, required, done)
}

@Singleton
class DayCloser @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val taskDao: TaskDao,
    private val routineDao: RoutineDao,
    private val recordDao: RecordDao,
    private val database: AppDatabase,
    private val projectRepository: com.example.taskflow.data.repositories.ProjectRepository
) {
    suspend fun closeDaysUpTo(targetEpochDay: Long) {
        val lastClosed = settingsRepo.getLastClosedDaySync()
        if (lastClosed == 0L) {
            settingsRepo.setLastClosedDay(targetEpochDay)
            return
        }
        if (lastClosed >= targetEpochDay) return

        val routines = routineDao.getAllRoutinesSync()
        val firstDay = maxOf(lastClosed + 1, targetEpochDay - 400)
        for (day in firstDay..targetEpochDay) {
            database.withTransaction {
                closeDay(day, routines)
            }
        }
        settingsRepo.setLastClosedDay(targetEpochDay)

        if (settingsRepo.overdueModeFlow.first() == "MOVE_TO_TODAY") {
            taskDao.moveOverdueToDate(targetEpochDay + 1)
        }
    }

    private suspend fun closeDay(day: Long, routines: List<RoutineEntity>) {
        val completedIds = routineDao.getAllCompletionsOnDateSync(day).map { it.routineTaskId }.toSet()
        val monthStart = LocalDate.ofEpochDay(day).withDayOfMonth(1).toEpochDay()
        val results = mutableListOf<RoutineDayResult>()

        for (routine in routines) {
            val tasks = routineDao.getTasksForRoutineSync(routine.id)
            val graceUsed = recordDao.countGraceDays(routine.id, monthStart, day - 1)
            val result = evaluateRoutineDay(routine, tasks, completedIds, day, graceUsed) ?: continue
            recordDao.insertRoutineDayRecord(RoutineDayRecordEntity(routine.id, day, result.status, result.required, result.done))
            results.add(result)
        }

        val topTasks = taskDao.getTasksForDateSync(day).filter { it.parentTaskId == null }
        val global = evaluateGlobalDay(
            results,
            topTasks.size,
            topTasks.count { it.isCompleted }
        )
        recordDao.insertDayRecord(DayRecordEntity(day, global.status, global.required, global.done))
    }
}
