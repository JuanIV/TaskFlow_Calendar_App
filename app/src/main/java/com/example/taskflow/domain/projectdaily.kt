package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectDailyLogEntity

// Registro planificado y hecho de un dia para el cierre (solo cuenta el trabajo de hojas)
data class DayProjectTotals(val planned: Int, val done: Int)

// Suma de minutos planificados y hechos de un dia a partir de los registros guardados.
// planned cuenta partes (en minutos); se usa como requiredCount por parte, igual que las tareas.
fun totalsForDay(logs: List<ProjectDailyLogEntity>): DayProjectTotals =
    DayProjectTotals(
        planned = logs.count { it.plannedMinutes > 0 },
        done = logs.count { it.plannedMinutes > 0 && it.doneMinutes >= it.plannedMinutes }
    )

// Minutos hechos de una parte, tope de lo planificado
fun clampedDone(plannedMinutes: Int, doneMinutes: Int): Int =
    minOf(plannedMinutes, maxOf(0, doneMinutes))

// Suma de minutos registrados de una hoja en un dia: planificados y hechos
fun mergeDailyLog(existing: ProjectDailyLogEntity?, nodeId: Long, day: Long, planned: Int, done: Int): ProjectDailyLogEntity =
    ProjectDailyLogEntity(
        nodeId = nodeId,
        epochDay = day,
        plannedMinutes = planned,
        doneMinutes = maxOf(existing?.doneMinutes ?: 0, done)
    )
