package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity

// Los proyectos avanzados no pasan por el planificador. Estas funciones dan un
// ProjectPlan y un ProjectSummary sinteticos para que las pantallas existentes funcionen.

fun advancedPlanStatus(nodes: List<ProjectNodeEntity>): String {
    val leaves = leafNodeIds(nodes)
    val pending = nodes.any { it.id in leaves && !it.completed }
    return if (pending) PlanStatus.SIN_DEADLINE else PlanStatus.SIN_PENDIENTE
}

fun advancedProjectPlan(today: Long, nodes: List<ProjectNodeEntity>): ProjectPlan = ProjectPlan(
    today = today,
    status = advancedPlanStatus(nodes),
    expectedEndDay = null,
    marginDays = null,
    overdueDays = 0,
    progressPercent = advancedProgressPercent(nodes),
    remainingMinutes = 0,
    nodes = emptyMap(),
    parts = emptyList()
)

fun advancedProjectSummary(projectId: Long, nodes: List<ProjectNodeEntity>): ProjectSummary = ProjectSummary(
    projectId = projectId,
    status = advancedPlanStatus(nodes),
    progressPercent = advancedProgressPercent(nodes),
    expectedEndDay = null,
    marginDays = null,
    overdueDays = 0,
    todayLabel = null,
    todayExtraCount = 0
)
