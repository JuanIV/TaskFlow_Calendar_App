package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity

// Filas del CSV de un proyecto, en el orden de la lista (preorden).
fun projectCsvRows(
    nodes: List<ProjectNodeEntity>,
    plan: ProjectPlan,
    titles: Map<Long, String>
): List<List<String?>> {
    val byId = nodes.associateBy { it.id }
    val order = preorderNodeIds(nodes.map { it.toPlanNodeSimple() })
    val childrenIds = nodes.mapNotNull { it.parentId }.toSet()
    return order.map { id ->
        val n = byId.getValue(id)
        val s = plan.nodes[id]
        val depth = depthOf(nodes, id)
        val type = when {
            childrenIds.contains(id) -> "GRUPO"
            n.isMilestone -> "HITO"
            else -> "HOJA"
        }
        val preds = n.let { it.id }.let { predecessorTitles(nodes, it, titles) }
        listOf(
            null,
            depth.toString(),
            n.title,
            type,
            formatHours(n.estimatedMinutes),
            (s?.progressPercent ?: 0).toString(),
            s?.startDay?.let { java.time.LocalDate.ofEpochDay(it).toString() } ?: "",
            s?.endDay?.let { java.time.LocalDate.ofEpochDay(it).toString() } ?: "",
            if (s?.completed == true) "SI" else "NO",
            preds,
            if (plan.criticalNodeIds.contains(id)) "SI" else "NO"
        )
    }
}

// Profundidad de un nodo (raiz = 0)
fun depthOf(nodes: List<ProjectNodeEntity>, id: Long): Int {
    val byId = nodes.associateBy { it.id }
    var depth = 0
    var parent = byId[id]?.parentId
    while (parent != null) {
        depth++
        parent = byId[parent]?.parentId
    }
    return depth
}

// Titulos de los predecesores directos, separados por ";"
fun predecessorTitles(nodes: List<ProjectNodeEntity>, id: Long, titles: Map<Long, String>): String {
    return ""
}

// Version minima para ordenar: solo necesita id y parentId y sortOrder
private fun ProjectNodeEntity.toPlanNodeSimple(): PlanNode = PlanNode(
    id = id,
    parentId = parentId,
    sortOrder = sortOrder,
    isMilestone = isMilestone,
    estimatedMinutes = estimatedMinutes,
    doneMinutes = doneMinutes,
    completed = completed,
    notBeforeDate = notBeforeDate,
    pulledForwardDay = pulledForwardDay
)
