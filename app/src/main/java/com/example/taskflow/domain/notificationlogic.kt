package com.example.taskflow.domain

import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskCompletionEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.data.entities.TaskEntity

fun getTodayTasksForNotification(
    allTasks: List<TaskEntity>,
    todayEpoch: Long,
    overdueMode: String
): List<TaskEntity> {
    return allTasks.filter { task ->
        task.parentTaskId == null && task.deletedAt == null && !task.isCompleted &&
        (task.date == todayEpoch || (overdueMode == "SHOW" && task.date != null && task.date < todayEpoch))
    }.sortedWith(compareByDescending<TaskEntity> { it.priority }.thenBy { it.sortOrder })
}

fun getRoutineTasksForNotification(
    tasks: List<RoutineTaskEntity>,
    completions: List<RoutineTaskCompletionEntity>,
    todayEpoch: Long
): List<Pair<RoutineTaskEntity, Boolean>> {
    val applicableTasks = tasks.filter { taskAppliesOn(it, todayEpoch) && it.deletedAtEpochDay == null }
    val mandatoryCount = applicableTasks.count { !it.isOptional }
    val treatAllAsMandatory = mandatoryCount == 0

    return applicableTasks.sortedWith(
        compareBy<RoutineTaskEntity> { if (treatAllAsMandatory) 0 else if (it.isOptional) 1 else 0 }
        .thenBy { it.sortOrder }
    ).map { task ->
        val isCompleted = completions.any { it.routineTaskId == task.id }
        Pair(task, isCompleted)
    }
}

fun isRoutineNotificationVisible(
    routine: RoutineEntity,
    todayEpoch: Long,
    currentMinute: Int,
    dayStartHour: Int,
    applicableTasks: List<RoutineTaskEntity>,
    completions: List<RoutineTaskCompletionEntity>
): Boolean {
    if (!routineAppliesOn(routine, todayEpoch)) return false

    val startMin = routine.startMinuteOfDay
    val endMin = routine.endMinuteOfDay
    val offNow = getLogicalMinuteOffset(currentMinute, dayStartHour)
    val offStart = getLogicalMinuteOffset(startMin, dayStartHour)

    if (endMin == null) {
        if (offNow < offStart) return false
    } else {
        if (!isMinuteInLogicalWindow(currentMinute, startMin, endMin, dayStartHour)) return false
    }

    val mandatoryTasks = applicableTasks.filter { !it.isOptional && it.deletedAtEpochDay == null && taskAppliesOn(it, todayEpoch) }
    val checkTasks = if (mandatoryTasks.isEmpty()) applicableTasks.filter { it.deletedAtEpochDay == null && taskAppliesOn(it, todayEpoch) } else mandatoryTasks

    if (checkTasks.isEmpty()) return false

    val allDone = checkTasks.all { t -> completions.any { it.routineTaskId == t.id } }
    return !allDone
}
