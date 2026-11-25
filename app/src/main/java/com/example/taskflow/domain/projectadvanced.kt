package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import java.time.Instant
import java.time.ZoneId

// Ids de las hojas de trabajo: nodos que no son hito y no tienen hijos
fun leafNodeIds(nodes: List<ProjectNodeEntity>): Set<Long> {
    val parents = nodes.mapNotNull { it.parentId }.toSet()
    return nodes.filter { !it.isMilestone && it.id !in parents }.map { it.id }.toSet()
}

// Progreso de un proyecto avanzado: hojas completadas / hojas totales, redondeado
fun advancedProgressPercent(nodes: List<ProjectNodeEntity>): Int {
    val leaves = leafNodeIds(nodes)
    if (leaves.isEmpty()) return 0
    val done = nodes.count { it.id in leaves && it.completed }
    return (done * 100 + leaves.size / 2) / leaves.size
}

// Dia logico (epochDay) al que pertenece un instante, segun la hora de inicio del dia
fun logicalDayOfMillis(ms: Long, dayStartHour: Int, zone: ZoneId): Long {
    val z = Instant.ofEpochMilli(ms).atZone(zone)
    val adjusted = if (z.hour < dayStartHour) z.minusDays(1) else z
    return adjusted.toLocalDate().toEpochDay()
}
