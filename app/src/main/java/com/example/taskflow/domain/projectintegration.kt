package com.example.taskflow.domain

// Fila de proyecto que se muestra en Tareas o en el widget "Tareas de hoy"
data class ProjectTodayItem(
    val projectId: Long,
    val projectName: String,
    val nodeId: Long,
    val partIndex: Int,
    val totalParts: Int,
    val minutes: Int,
    val label: String,
    val completed: Boolean
)

// Etiqueta "Proyecto: Tarea (parte i/n)" o "Proyecto: Tarea" si hay una sola parte
fun projectTodayLabel(projectName: String, part: PlanPart, title: String): String =
    projectName + ": " + partLabel(title, part)

// Partes de hoy de un proyecto, ya convertidas en filas
fun projectTodayItems(
    projectId: Long,
    projectName: String,
    plan: ProjectPlan,
    titles: Map<Long, String>,
    today: Long
): List<ProjectTodayItem> =
    todayParts(plan, today).map { part ->
        ProjectTodayItem(
            projectId = projectId,
            projectName = projectName,
            nodeId = part.nodeId,
            partIndex = part.partIndex,
            totalParts = part.totalParts,
            minutes = part.minutes,
            label = projectTodayLabel(projectName, part, titles[part.nodeId].orEmpty()),
            completed = false
        )
    }

// Decide si hay que avisar del cambio de estado de un proyecto frente a su deadline.
// Devuelve el estado que debe guardarse como ultimo notificado, y el tipo de aviso
// (null = no avisar). Solo se avisa al entrar en JUSTO o en ATRASADO desde un estado mejor.
enum class RiskAlert { NONE, AT_RISK, NO_MARGIN }

fun riskAlertFor(previous: String?, current: String): RiskAlert {
    val wasOk = previous == null || previous == PlanStatus.OK || previous == PlanStatus.SIN_DEADLINE
    return when (current) {
        PlanStatus.ATRASADO -> if (previous != PlanStatus.ATRASADO) RiskAlert.NO_MARGIN else RiskAlert.NONE
        PlanStatus.JUSTO -> if (wasOk) RiskAlert.AT_RISK else RiskAlert.NONE
        else -> RiskAlert.NONE
    }
}
