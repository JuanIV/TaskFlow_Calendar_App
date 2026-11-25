package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Estado de una celda de la rejilla para una hoja o un grupo en un dia o semana
data class GridCell(
    val epochDay: Long,
    val planned: Boolean,
    val completed: Boolean,
    val minutes: Int
)

// Una fila de la rejilla: un nodo visible del arbol
data class GridRow(
    val nodeId: Long,
    val rootId: Long,
    val depth: Int,
    val title: String,
    val isGroup: Boolean,
    val isMilestone: Boolean,
    val checkable: Boolean,
    val completed: Boolean,
    val cells: List<GridCell>
)

// Rango de dias de la rejilla (inclusive)
data class GridDays(val fromDay: Long, val toDay: Long) {
    val size: Int get() = (toDay - fromDay + 1).toInt()
    fun dayAt(index: Int): Long = fromDay + index
}

// Celda de semana: agrupa siete dias consecutivos desde el primer dia de semana configurado
data class WeekCell(
    val weekStart: Long,
    val planned: Boolean,
    val completed: Boolean,
    val minutes: Int
)

// Rango de dias de un proyecto para la rejilla: desde el inicio (o hoy) hasta el ultimo dia
// con trabajo, el deadline o hoy, con tres dias de margen.
fun gridDaysOf(
    startDate: Long,
    expectedEndDay: Long?,
    deadlineDate: Long?,
    today: Long
): GridDays {
    val from = minOf(startDate, today)
    val last = listOfNotNull(expectedEndDay, deadlineDate, today).maxOrNull() ?: today
    return GridDays(from, maxOf(from, last + 3))
}

// Primer dia de la semana (epochDay) que contiene el dia indicado
fun weekStartOfDay(epochDay: Long, firstDay: DayOfWeek): Long =
    LocalDate.ofEpochDay(epochDay)
        .with(TemporalAdjusters.previousOrSame(firstDay))
        .toEpochDay()

// Construye las celdas diarias de una hoja a partir de los dias planificados y los completados.
// plannedMinutes: mapa dia -> minutos planificados. completedDays: dias en los que la hoja esta completa.
fun leafCells(
    days: GridDays,
    plannedMinutes: Map<Long, Int>,
    completedNow: Boolean
): List<GridCell> = (0 until days.size).map { i ->
    val day = days.dayAt(i)
    val minutes = plannedMinutes[day] ?: 0
    val planned = minutes > 0
    // Regla: una celda planificada se marca completada solo si la hoja esta completa.
    // No se distingue dia pasado de dia futuro: el modelo no guarda que dia se trabajo realmente.
    // Limitacion conocida: una hoja completada antes de tiempo pinta como completados sus dias futuros.
    GridCell(
        epochDay = day,
        planned = planned,
        completed = planned && completedNow,
        minutes = minutes
    )
}

// Filas de la rejilla en preorden.
// nodes: todos los nodos del proyecto.
// leafPlanned: mapa nodeId -> (dia -> minutos planificados). Solo para hojas.
// leafCompleted: ids de hojas completas.
fun buildGridRows(
    nodes: List<ProjectNodeEntity>,
    days: GridDays,
    leafPlanned: Map<Long, Map<Long, Int>>,
    leafCompleted: Set<Long>,
    collapsed: Set<Long>
): List<GridRow> {
    val childrenMap: Map<Long?, List<ProjectNodeEntity>> = nodes
        .groupBy { it.parentId }
        .mapValues { entry -> entry.value.sortedWith(compareBy<ProjectNodeEntity>({ it.sortOrder }, { it.id })) }
    val byId = nodes.associateBy { it.id }
    val out = ArrayList<GridRow>()

    // Un grupo se marca completado si todas sus hojas lo estan, aunque ninguna tenga dias planificados.
    // Es coherente con la regla de la hoja; la UI puede mostrarlo como completado sin celdas.
    // Devuelve las celdas planificadas de un subarbol como dia -> minutos, y si todo el subarbol esta completo
    fun subtree(node: ProjectNodeEntity): Pair<Map<Long, Int>, Boolean> {
        val kids = childrenMap[node.id].orEmpty()
        if (kids.isEmpty()) {
            return Pair(leafPlanned[node.id].orEmpty(), leafCompleted.contains(node.id))
        }
        val merged = HashMap<Long, Int>()
        var allDone = true
        for (kid in kids) {
            val (m, done) = subtree(kid)
            for ((day, minutes) in m) merged[day] = (merged[day] ?: 0) + minutes
            if (!done) allDone = false
        }
        return Pair(merged, allDone)
    }

    fun rootOf(node: ProjectNodeEntity): Long {
        var cur = node
        while (true) {
            val pid = cur.parentId ?: break
            cur = byId.getValue(pid)
        }
        return cur.id
    }

    fun visit(parentId: Long?, depth: Int) {
        for (node in childrenMap[parentId].orEmpty()) {
            val kids = childrenMap[node.id].orEmpty()
            val isGroup = kids.isNotEmpty()
            val (plannedMap, done) = subtree(node)
            val cells = leafCells(days, plannedMap, done)
            out.add(
                GridRow(
                    nodeId = node.id,
                    rootId = rootOf(node),
                    depth = depth,
                    title = node.title,
                    isGroup = isGroup,
                    isMilestone = node.isMilestone,
                    checkable = !isGroup,
                    completed = done,
                    cells = cells
                )
            )
            if (isGroup && !collapsed.contains(node.id)) visit(node.id, depth + 1)
        }
    }
    visit(null, 0)
    return out
}

// Agregacion por semanas de una lista de celdas diarias. Una semana se marca si tiene algun dia planificado.
// Se completa si todos sus dias planificados estan completados.
fun weekCellsOf(cells: List<GridCell>, firstDay: DayOfWeek): List<WeekCell> {
    val groups = cells.groupBy { weekStartOfDay(it.epochDay, firstDay) }
    return groups.keys.sorted().map { weekStart ->
        val inWeek = groups.getValue(weekStart)
        val planned = inWeek.filter { it.planned }
        WeekCell(
            weekStart = weekStart,
            planned = planned.isNotEmpty(),
            completed = planned.isNotEmpty() && planned.all { it.completed },
            minutes = planned.sumOf { it.minutes }
        )
    }
}

// Convierte un conjunto de asignaciones manuales (nodeId, dia) en el mapa que necesita buildGridRows.
// Cada dia asignado cuenta 60 minutos, igual que leafPlannedFromAssignments del repositorio.
fun assignmentsToLeafPlanned(cells: Set<Pair<Long, Long>>): Map<Long, Map<Long, Int>> {
    val out = HashMap<Long, MutableMap<Long, Int>>()
    for ((nodeId, day) in cells) {
        out.getOrPut(nodeId) { HashMap() }[day] = 60
    }
    return out
}

// Una celda solo es pintable a mano si su nodo es una hoja: no es grupo ni hito.
fun isAssignableLeaf(nodes: List<ProjectNodeEntity>, nodeId: Long): Boolean {
    val node = nodes.firstOrNull { it.id == nodeId } ?: return false
    if (node.isMilestone) return false
    return nodes.none { it.parentId == nodeId }
}
