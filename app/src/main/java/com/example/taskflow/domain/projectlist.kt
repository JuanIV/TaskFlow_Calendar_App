package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity

enum class ListFilter { ALL, PENDING, TODAY, LATE }

data class ListRow(
    val nodeId: Long,
    val depth: Int,
    val title: String,
    val isGroup: Boolean,
    val isMilestone: Boolean,
    val completed: Boolean,
    val estimatedMinutes: Int,
    val progressPercent: Int,
    val startDay: Long?,
    val endDay: Long?,
    val expanded: Boolean,
    val late: Boolean,
    val todayLabel: String?,
    val critical: Boolean = false
)

// Construye las filas visibles de la vista Lista, en preorden.
// Un nodo aparece si cumple el filtro o si tiene descendientes que lo cumplen.
// Los hijos de un grupo plegado no aparecen.
fun buildListRows(
    nodes: List<ProjectNodeEntity>,
    plan: ProjectPlan,
    deadlineDate: Long?,
    today: Long,
    filter: ListFilter,
    collapsed: Set<Long>
): List<ListRow> {
    val childrenMap: Map<Long?, List<ProjectNodeEntity>> = nodes
        .groupBy { it.parentId }
        .mapValues { entry -> entry.value.sortedWith(compareBy<ProjectNodeEntity>({ it.sortOrder }, { it.id })) }
    val todayPartByNode: Map<Long, PlanPart> = plan.parts
        .filter { it.day == today }
        .associateBy { it.nodeId }

    fun isLate(id: Long): Boolean {
        val s = plan.nodes[id] ?: return false
        val deadline = deadlineDate ?: return false
        val end = s.endDay ?: return false
        return !s.completed && end > deadline
    }

    fun matchesSelf(node: ProjectNodeEntity): Boolean {
        val s = plan.nodes[node.id]
        return when (filter) {
            ListFilter.ALL -> true
            ListFilter.PENDING -> !(s?.completed ?: false)
            ListFilter.TODAY -> todayPartByNode.containsKey(node.id)
            ListFilter.LATE -> isLate(node.id)
        }
    }

    val matchCache = HashMap<Long, Boolean>()
    fun subtreeMatches(node: ProjectNodeEntity): Boolean {
        val cached = matchCache[node.id]
        if (cached != null) return cached
        val result = matchesSelf(node) || (childrenMap[node.id]?.any { subtreeMatches(it) } ?: false)
        matchCache[node.id] = result
        return result
    }

    val out = ArrayList<ListRow>()
    fun visit(parentId: Long?, depth: Int) {
        for (node in childrenMap[parentId].orEmpty()) {
            if (!subtreeMatches(node)) continue
            val s = plan.nodes[node.id]
            val isGroup = childrenMap.containsKey(node.id)
            val part = todayPartByNode[node.id]
            out.add(
                ListRow(
                    nodeId = node.id,
                    depth = depth,
                    title = node.title,
                    isGroup = isGroup,
                    isMilestone = node.isMilestone,
                    completed = s?.completed ?: node.completed,
                    estimatedMinutes = node.estimatedMinutes,
                    progressPercent = s?.progressPercent ?: 0,
                    startDay = s?.startDay,
                    endDay = s?.endDay,
                    expanded = !collapsed.contains(node.id),
                    late = isLate(node.id),
                    todayLabel = if (part != null) partLabel(node.title, part) else null,
                    critical = plan.criticalNodeIds.contains(node.id)
                )
            )
            if (isGroup && !collapsed.contains(node.id)) visit(node.id, depth + 1)
        }
    }
    visit(null, 0)
    return out
}
