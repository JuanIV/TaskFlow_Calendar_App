package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity

// Resumen de desviacion de un conjunto de hojas completadas con tiempo real
data class DeviationSummary(
    val sampleCount: Int,
    val averageEstimated: Double,
    val averageActual: Double,
    val deviationPercent: Double
)

// Solo cuentan hojas completadas con tiempo real registrado y estimacion mayor que cero
fun deviationSummary(nodes: List<ProjectNodeEntity>): DeviationSummary {
    val samples = nodes.filter {
        !it.isMilestone && it.completed && it.actualMinutes != null && it.estimatedMinutes > 0
    }
    if (samples.isEmpty()) return DeviationSummary(0, 0.0, 0.0, 0.0)
    val avgEstimated = samples.map { it.estimatedMinutes.toDouble() }.average()
    val avgActual = samples.map { (it.actualMinutes ?: 0).toDouble() }.average()
    val percent = if (avgEstimated <= 0.0) 0.0 else (avgActual - avgEstimated) / avgEstimated * 100.0
    return DeviationSummary(samples.size, avgEstimated, avgActual, percent)
}

// Texto corto con signo: "+12 %" o "-8 %"
fun formatDeviation(percent: Double): String {
    val rounded = Math.round(percent).toInt()
    return if (rounded > 0) "+$rounded %" else "$rounded %"
}
