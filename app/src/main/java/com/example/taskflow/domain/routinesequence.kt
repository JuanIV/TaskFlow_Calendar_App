package com.example.taskflow.domain

import com.example.taskflow.data.entities.RoutineTaskEntity

// Orden de la secuencia: primero las obligatorias y luego las opcionales, cada grupo por sortOrder
fun orderForSequence(tasks: List<RoutineTaskEntity>): List<RoutineTaskEntity> =
    tasks.sortedWith(compareBy({ it.isOptional }, { it.sortOrder }))

fun firstPendingInSequence(ordered: List<RoutineTaskEntity>, doneIds: Set<Long>): Long? =
    ordered.firstOrNull { it.id !in doneIds }?.id

// Marcar exige tener hechas todas las anteriores. Desmarcar solo se permite si ninguna posterior esta hecha.
fun canToggleInSequence(sequential: Boolean, ordered: List<RoutineTaskEntity>, doneIds: Set<Long>, taskId: Long): Boolean {
    if (!sequential) return true
    val index = ordered.indexOfFirst { it.id == taskId }
    if (index == -1) return true
    return if (taskId in doneIds) {
        ordered.drop(index + 1).none { it.id in doneIds }
    } else {
        ordered.take(index).all { it.id in doneIds }
    }
}
