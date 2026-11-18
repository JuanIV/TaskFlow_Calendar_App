package com.example.taskflow.domain

import com.example.taskflow.data.entities.TaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Siguiente fecha (epochDay) de una tarea recurrente a partir de "date".
fun nextTaskDate(date: Long, type: String, interval: Int, weekdaysMask: Int): Long? {
    val step = interval.coerceAtLeast(1)
    val d = LocalDate.ofEpochDay(date)
    return when (type) {
        "DAILY" -> d.plusDays(step.toLong()).toEpochDay()
        "WEEKLY" -> {
            if (weekdaysMask == 0) {
                d.plusWeeks(step.toLong()).toEpochDay()
            } else {
                val startMonday = d.with(DayOfWeek.MONDAY)
                var candidate = d.plusDays(1)
                var guard = 0
                var found: Long? = null
                while (guard < 7 * step + 14 && found == null) {
                    val bit = 1 shl (candidate.dayOfWeek.value - 1)
                    if ((weekdaysMask and bit) != 0) {
                        val weeks = ChronoUnit.WEEKS.between(startMonday, candidate.with(DayOfWeek.MONDAY))
                        if (weeks % step == 0L) found = candidate.toEpochDay()
                    }
                    candidate = candidate.plusDays(1)
                    guard++
                }
                found ?: d.plusWeeks(step.toLong()).toEpochDay()
            }
        }
        "MONTHLY" -> d.plusMonths(step.toLong()).toEpochDay()
        "YEARLY" -> d.plusYears(step.toLong()).toEpochDay()
        else -> null
    }
}

// Fecha de la siguiente repeticion al completar la tarea. Siempre posterior a hoy.
fun nextSpawnDate(task: TaskEntity, todayEpoch: Long): Long? {
    if (task.recurrenceType == "NONE") return null
    val base = task.date ?: todayEpoch
    var next = nextTaskDate(base, task.recurrenceType, task.recurrenceInterval, task.recurrenceWeekdaysMask) ?: return null
    var guard = 0
    while (next <= todayEpoch && guard < 1000) {
        next = nextTaskDate(next, task.recurrenceType, task.recurrenceInterval, task.recurrenceWeekdaysMask) ?: return null
        guard++
    }
    return next
}
