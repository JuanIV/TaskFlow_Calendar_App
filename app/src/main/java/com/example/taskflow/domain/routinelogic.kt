package com.example.taskflow.domain

import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import java.time.LocalDate

fun isRoutinePausedOn(routine: RoutineEntity, targetEpochDay: Long): Boolean {
    if (routine.pausedFromEpochDay == null) return false
    if (targetEpochDay < routine.pausedFromEpochDay) return false
    if (routine.pausedUntilEpochDay != null && targetEpochDay > routine.pausedUntilEpochDay) return false
    return true
}

fun routineWouldApplyIgnoringPause(routine: RoutineEntity, targetEpochDay: Long): Boolean {
    if (!routine.isActive || routine.isTemplate) return false

    if (routine.startDate != null && targetEpochDay < routine.startDate) return false
    if (routine.endDate != null && targetEpochDay > routine.endDate) return false
    if (targetEpochDay < routine.anchorDate) return false

    return if (routine.recurrenceType == "WEEKDAYS") {
        val date = LocalDate.ofEpochDay(targetEpochDay)
        val dayBit = 1 shl (date.dayOfWeek.value - 1)
        (routine.weekdaysMask and dayBit) != 0
    } else {
        val daysBetween = targetEpochDay - routine.anchorDate
        val interval = routine.intervalDays.coerceAtLeast(1)
        (daysBetween % interval) == 0L
    }
}

fun routineAppliesOn(routine: RoutineEntity, targetEpochDay: Long): Boolean {
    if (isRoutinePausedOn(routine, targetEpochDay)) return false
    return routineWouldApplyIgnoringPause(routine, targetEpochDay)
}

fun taskAppliesOn(task: RoutineTaskEntity, targetEpochDay: Long): Boolean {
    if (task.weekdaysMask == null || task.weekdaysMask == 0) return true
    val date = LocalDate.ofEpochDay(targetEpochDay)
    val dayBit = 1 shl (date.dayOfWeek.value - 1)
    return (task.weekdaysMask and dayBit) != 0
}
