package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity

enum class GanttZoom(val dayWidthDp: Int) {
    DAY(24),
    WEEK(8),
    MONTH(3)
}

// Fila del Gantt: datos ya calculados, sin fechas propias
data class GanttRow(
    val nodeId: Long,
    val depth: Int,
    val title: String,
    val isGroup: Boolean,
    val isMilestone: Boolean,
    val completed: Boolean,
    val progressPercent: Int,
    val startDay: Long?,
    val endDay: Long?,
    val critical: Boolean,
    val predecessorIds: List<Long>,
    val estimatedMinutes: Int = 0
)

// Rango del eje temporal (inclusive)
data class GanttRange(val fromDay: Long, val toDay: Long) {
    val days: Long get() = toDay - fromDay + 1
}

// Filas del Gantt: mismas filas que la vista Lista con filtro ALL
fun buildGanttRows(
    nodes: List<ProjectNodeEntity>,
    dependencies: Map<Long, List<Long>>,
    plan: ProjectPlan,
    deadlineDate: Long?,
    today: Long,
    collapsed: Set<Long>
): List<GanttRow> {
    val rows = buildListRows(nodes, plan, deadlineDate, today, ListFilter.ALL, collapsed)
    return rows.map { r ->
        GanttRow(
            nodeId = r.nodeId,
            depth = r.depth,
            title = r.title,
            isGroup = r.isGroup,
            isMilestone = r.isMilestone,
            completed = r.completed,
            progressPercent = r.progressPercent,
            startDay = r.startDay,
            endDay = r.endDay,
            critical = r.critical,
            predecessorIds = dependencies[r.nodeId].orEmpty(),
            estimatedMinutes = nodes.firstOrNull { it.id == r.nodeId }?.estimatedMinutes ?: 0
        )
    }
}

// Eje temporal: desde el primer dia relevante hasta el ultimo mas 3 dias de margen
fun ganttRangeOf(plan: ProjectPlan, startDate: Long, deadlineDate: Long?, today: Long): GanttRange {
    val firstCandidates = listOf(startDate, today)
    val from = firstCandidates.minOrNull() ?: today
    val lastCandidates = listOfNotNull(plan.expectedEndDay, deadlineDate, today)
    val to = (lastCandidates.maxOrNull() ?: today) + 3
    return GanttRange(from, maxOf(from, to))
}

// Posicion X (px) de un dia dentro del eje
fun dayToX(day: Long, range: GanttRange, dayWidthPx: Float): Float =
    ((day - range.fromDay).toFloat()) * dayWidthPx

// Ancho (px) de un rango de dias inclusivo
fun spanToWidth(startDay: Long, endDay: Long, dayWidthPx: Float): Float =
    ((endDay - startDay + 1).toFloat()) * dayWidthPx

// Dependencias agrupadas por sucesor, usando los ids de nodos
fun dependencyMap(pairs: List<Pair<Long, Long>>): Map<Long, List<Long>> =
    pairs.groupBy({ it.second }, { it.first })
