package com.example.taskflow.domain

data class ProjectSummary(
    val projectId: Long,
    val status: String,
    val progressPercent: Int,
    val expectedEndDay: Long?,
    val marginDays: Int?,
    val overdueDays: Int,
    val todayLabel: String?,
    val todayExtraCount: Int
)

// Resumen de un proyecto para el listado. nodes ya viene convertido a PlanNode.
// titles mapea id de nodo a titulo (para la etiqueta "Nombre (parte i/n)").
fun summarizeProject(
    projectId: Long,
    config: PlanConfig,
    nodes: List<PlanNode>,
    titles: Map<Long, String>,
    today: Long
): ProjectSummary {
    val plan = planProject(config, nodes, today)
    val parts = todayParts(plan, today)
    val first = parts.firstOrNull()
    return ProjectSummary(
        projectId = projectId,
        status = plan.status,
        progressPercent = plan.progressPercent,
        expectedEndDay = plan.expectedEndDay,
        marginDays = plan.marginDays,
        overdueDays = plan.overdueDays,
        todayLabel = if (first != null) partLabel(titles[first.nodeId].orEmpty(), first) else null,
        todayExtraCount = maxOf(0, parts.size - 1)
    )
}
