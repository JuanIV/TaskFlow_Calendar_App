package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Techo de dias de la rejilla y salto de ampliacion (4 semanas)
const val MAX_GRID_DAYS = 730
const val GRID_EXTEND_DAYS = 28

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
    val cells: List<GridCell>,
    // Color elegido por el usuario en el nodo raiz de esta fila (null = paleta automatica)
    val rootColorArgb: Int? = null
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

// Rango inicial de la rejilla de un proyecto avanzado, en semanas completas:
// desde min(inicio, hoy - 7, primera celda) hasta max(hoy + 8 semanas, ultima celda + 14, deadline).
fun initialGridDays(
    startDate: Long,
    assignmentDays: Collection<Long>,
    deadlineDate: Long?,
    today: Long,
    firstDay: DayOfWeek
): GridDays {
    val from0 = minOf(startDate, today - 7, assignmentDays.minOrNull() ?: Long.MAX_VALUE)
    val to0 = maxOf(today + 56, (assignmentDays.maxOrNull() ?: today) + 14, deadlineDate ?: today)
    val from = weekStartOfDay(from0, firstDay)
    val to = weekStartOfDay(to0, firstDay) + 6
    return GridDays(from, to)
}

// Amplia un rango base por delante y por detras
fun extendedGridDays(base: GridDays, backDays: Int, forwardDays: Int): GridDays =
    GridDays(base.fromDay - backDays, base.toDay + forwardDays)

// Numero de semana respecto a la semana de origen (la del inicio del proyecto es la 0)
fun weekNumberOf(weekStart: Long, originWeekStart: Long): Int =
    Math.floorDiv(weekStart - originWeekStart, 7L).toInt()

// Construye las celdas diarias de una hoja a partir de los dias planificados y los completados.
// plannedMinutes: mapa dia -> minutos planificados. completedNow: la hoja esta completa.
fun leafCells(
    days: GridDays,
    plannedMinutes: Map<Long, Int>,
    completedNow: Boolean
): List<GridCell> = (0 until days.size).map { i ->
    val day = days.dayAt(i)
    val minutes = plannedMinutes[day] ?: 0
    val planned = minutes > 0
    GridCell(
        epochDay = day,
        planned = planned,
        completed = planned && completedNow,
        minutes = minutes
    )
}

private class SubtreeInfo(
    val planned: Map<Long, Int>,
    val doneDays: Set<Long>,
    val pendingDays: Set<Long>,
    val allDone: Boolean
)

private fun cellsOf(days: GridDays, info: SubtreeInfo): List<GridCell> = (0 until days.size).map { i ->
    val day = days.dayAt(i)
    val minutes = info.planned[day] ?: 0
    val planned = minutes > 0
    GridCell(
        epochDay = day,
        planned = planned,
        completed = planned && day in info.doneDays && day !in info.pendingDays,
        minutes = minutes
    )
}

// Filas de la rejilla en preorden.
// nodes: todos los nodos del proyecto.
// leafPlanned: mapa nodeId -> (dia -> minutos planificados). Solo para hojas.
// leafCompleted: ids de hojas completas.
// completionDays: dia logico en que se completo cada hoja completa (si falta, vale para todos sus dias).
// completedCells: pares (nodo, dia) cuya tarea de ese dia esta completada.
// Una celda de hoja esta hecha si su tarea esta completada, o si la hoja esta completa y el dia
// es anterior o igual al de completado. Una celda de grupo esta hecha si lo estan todas las hojas con celda ese dia.
fun buildGridRows(
    nodes: List<ProjectNodeEntity>,
    days: GridDays,
    leafPlanned: Map<Long, Map<Long, Int>>,
    leafCompleted: Set<Long>,
    collapsed: Set<Long>,
    completionDays: Map<Long, Long> = emptyMap(),
    completedCells: Set<Pair<Long, Long>> = emptySet()
): List<GridRow> {
    val childrenMap: Map<Long?, List<ProjectNodeEntity>> = nodes
        .groupBy { it.parentId }
        .mapValues { entry -> entry.value.sortedWith(compareBy<ProjectNodeEntity>({ it.sortOrder }, { it.id })) }
    val byId = nodes.associateBy { it.id }
    val out = ArrayList<GridRow>()

    fun subtree(node: ProjectNodeEntity): SubtreeInfo {
        val kids = childrenMap[node.id].orEmpty()
        if (kids.isEmpty()) {
            val planned = leafPlanned[node.id].orEmpty().filter { it.value > 0 }
            val nodeDone = leafCompleted.contains(node.id)
            val done = HashSet<Long>()
            val pending = HashSet<Long>()
            for (day in planned.keys) {
                val isDone = nodeDone || completedCells.contains(Pair(node.id, day))
                if (isDone) done.add(day) else pending.add(day)
            }
            return SubtreeInfo(planned, done, pending, nodeDone)
        }
        val merged = HashMap<Long, Int>()
        val done = HashSet<Long>()
        val pending = HashSet<Long>()
        var allDone = true
        for (kid in kids) {
            val info = subtree(kid)
            for ((day, minutes) in info.planned) merged[day] = (merged[day] ?: 0) + minutes
            done.addAll(info.doneDays)
            pending.addAll(info.pendingDays)
            if (!info.allDone) allDone = false
        }
        return SubtreeInfo(merged, done, pending, allDone)
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
            val info = subtree(node)
            val rootId = rootOf(node)
            out.add(
                GridRow(
                    nodeId = node.id,
                    rootId = rootId,
                    depth = depth,
                    title = node.title,
                    isGroup = isGroup,
                    isMilestone = node.isMilestone,
                    checkable = !isGroup,
                    completed = info.allDone,
                    cells = cellsOf(days, info),
                    rootColorArgb = byId[rootId]?.colorArgb
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
