package com.example.taskflow.domain

object ProjectStatus {
    const val ACTIVE = "ACTIVE"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val ARCHIVED = "ARCHIVED"
}

object PlanStatus {
    const val PAUSADO = "PAUSADO"
    const val SIN_PENDIENTE = "SIN_PENDIENTE"
    const val SIN_CAPACIDAD = "SIN_CAPACIDAD"
    const val SIN_DEADLINE = "SIN_DEADLINE"
    const val OK = "OK"
    const val JUSTO = "JUSTO"
    const val ATRASADO = "ATRASADO"
    const val CICLO = "CICLO"
}

// Nodo del arbol tal como lo necesita el motor. predecessors = ids de sus predecesores directos.
data class PlanNode(
    val id: Long,
    val parentId: Long?,
    val sortOrder: Int,
    val isMilestone: Boolean,
    val estimatedMinutes: Int,
    val doneMinutes: Int,
    val completed: Boolean,
    val notBeforeDate: Long?,
    val pulledForwardDay: Long?,
    val predecessors: List<Long> = emptyList()
)

data class PlanConfig(
    val startDate: Long,
    val deadlineDate: Long?,
    val dailyCapacityMinutes: Int,
    val workingDaysMask: Int,
    val excludedDays: Set<Long>,
    val paused: Boolean
)

// Un trozo de una hoja asignado a un dia
data class PlanPart(
    val nodeId: Long,
    val partIndex: Int,
    val totalParts: Int,
    val day: Long,
    val minutes: Int
)

// Fechas y progreso calculados de un nodo (grupo u hoja)
data class NodeSchedule(
    val nodeId: Long,
    val startDay: Long?,
    val endDay: Long?,
    val completed: Boolean,
    val progressPercent: Int
)

data class ProjectPlan(
    val today: Long,
    val status: String,
    val expectedEndDay: Long?,
    val marginDays: Int?,
    val overdueDays: Int,
    val progressPercent: Int,
    val remainingMinutes: Int,
    val nodes: Map<Long, NodeSchedule>,
    val parts: List<PlanPart>,
    val executionOrder: List<Long> = emptyList(),
    val criticalNodeIds: Set<Long> = emptySet()
)
