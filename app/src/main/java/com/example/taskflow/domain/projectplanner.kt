package com.example.taskflow.domain

import java.time.LocalDate

class NoWorkingDayException : RuntimeException("No hay dias laborables disponibles")

private const val MAX_SEARCH_DAYS = 3660
private const val MAX_HORIZON_DAYS = 36500

private class Segment(val nodeId: Long, val day: Long, val minutes: Int)

private class Allocation(val segments: List<Segment>, val zeroDates: Map<Long, Long>)

private class PlanTree(nodes: List<PlanNode>) {
    val byId: Map<Long, PlanNode> = nodes.associateBy { it.id }
    val children: Map<Long, List<PlanNode>> = nodes
        .filter { it.parentId != null }
        .groupBy { it.parentId!! }
        .mapValues { entry -> entry.value.sortedWith(compareBy<PlanNode>({ it.sortOrder }, { it.id })) }
    val roots: List<PlanNode> = nodes
        .filter { it.parentId == null }
        .sortedWith(compareBy<PlanNode>({ it.sortOrder }, { it.id }))

    fun isGroup(id: Long): Boolean = children.containsKey(id)
    fun childrenOf(id: Long): List<PlanNode> = children[id].orEmpty()
}

// Orden de ejecucion de las hojas y milestones. preds = predecesores efectivos (solo nodos de trabajo).
// Resultado de ordenar el trabajo. null en orderWork = ciclo de dependencias.
class WorkOrder(val order: List<PlanNode>, val preds: Map<Long, Set<Long>>)

fun dayBitOf(day: Long): Int = 1 shl (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)

fun isWorkingDay(day: Long, config: PlanConfig): Boolean =
    (config.workingDaysMask and dayBitOf(day)) != 0 && !config.excludedDays.contains(day)

fun nextWorkingDay(from: Long, config: PlanConfig): Long {
    var d = from
    var steps = 0
    while (!isWorkingDay(d, config)) {
        d++
        steps++
        if (steps > MAX_SEARCH_DAYS) throw NoWorkingDayException()
    }
    return d
}

// Milestones y nodos con estimacion 0 no consumen capacidad
fun isZeroLength(node: PlanNode): Boolean = node.isMilestone || node.estimatedMinutes <= 0

fun isEffectivelyDone(node: PlanNode): Boolean =
    if (isZeroLength(node)) node.completed
    else node.completed || node.doneMinutes >= node.estimatedMinutes

fun remainingMinutesOf(node: PlanNode): Int =
    if (isZeroLength(node) || isEffectivelyDone(node)) 0
    else maxOf(0, node.estimatedMinutes - node.doneMinutes)

// Registra minutos trabajados en una hoja (solo hojas con estimacion mayor que 0)
fun applyPartDone(node: PlanNode, minutes: Int): PlanNode {
    val newDone = minOf(node.estimatedMinutes, node.doneMinutes + maxOf(0, minutes))
    return node.copy(doneMinutes = newDone, completed = newDone >= node.estimatedMinutes)
}

// Vuelve una hoja a pendiente desde cero
fun reopenNode(node: PlanNode): PlanNode = node.copy(completed = false, doneMinutes = 0)

// Etiqueta visible: "Nombre (parte i/n)" si hay mas de una parte, si no solo el nombre
fun partLabel(title: String, part: PlanPart): String =
    if (part.totalParts > 1) "$title (parte ${part.partIndex}/${part.totalParts})" else title

// Partes que tocan en el dia indicado
fun todayParts(plan: ProjectPlan, today: Long): List<PlanPart> =
    plan.parts.filter { it.day == today }

// Orden de preorden (padres antes que hijos) de todos los nodos, incluidos grupos
fun preorderNodeIds(nodes: List<PlanNode>): List<Long> {
    val tree = PlanTree(nodes)
    val out = ArrayList<Long>()
    fun visit(list: List<PlanNode>) {
        for (n in list) {
            out.add(n.id)
            visit(tree.childrenOf(n.id))
        }
    }
    visit(tree.roots)
    return out
}

// Orden de preorden de hojas y milestones
private fun workSequence(tree: PlanTree): List<PlanNode> {
    val out = ArrayList<PlanNode>()
    fun visit(list: List<PlanNode>) {
        for (n in list) {
            if (tree.isGroup(n.id)) visit(tree.childrenOf(n.id)) else out.add(n)
        }
    }
    visit(tree.roots)
    return out
}

// Hojas no milestone de un subarbol (o la propia hoja)
private fun leavesUnder(tree: PlanTree, id: Long): List<PlanNode> {
    val node = tree.byId[id] ?: return emptyList()
    if (!tree.isGroup(id)) return if (isZeroLength(node)) emptyList() else listOf(node)
    return tree.childrenOf(id).flatMap { leavesUnder(tree, it.id) }
}

// Todos los nodos de trabajo (hojas y milestones) de un subarbol, o la propia hoja
private fun workNodesUnder(tree: PlanTree, id: Long): List<PlanNode> {
    val node = tree.byId[id] ?: return emptyList()
    if (!tree.isGroup(id)) return listOf(node)
    return tree.childrenOf(id).flatMap { workNodesUnder(tree, it.id) }
}

private fun isDoneNode(tree: PlanTree, id: Long): Boolean {
    val node = tree.byId[id] ?: return true
    if (!tree.isGroup(id)) return isEffectivelyDone(node)
    return tree.childrenOf(id).all { isDoneNode(tree, it.id) }
}

// Porcentaje por minutos: hecho / estimado total, redondeado
private fun progressOf(leaves: List<PlanNode>, fallbackDone: Boolean): Int {
    if (leaves.isEmpty()) return if (fallbackDone) 100 else 0
    var total = 0L
    var done = 0L
    for (n in leaves) {
        total += n.estimatedMinutes
        done += if (isEffectivelyDone(n)) n.estimatedMinutes else minOf(n.doneMinutes, n.estimatedMinutes)
    }
    return ((done * 100 + total / 2) / total).toInt()
}

// Ordena el trabajo respetando dependencias. Entre los nodos listos, gana el primero en orden de lista.
// Devuelve null si hay ciclo.
fun orderWork(nodes: List<PlanNode>): WorkOrder? {
    val tree = PlanTree(nodes)
    val sequence = workSequence(tree)
    val index = HashMap<Long, Int>()
    sequence.forEachIndexed { i, n -> index[n.id] = i }

    // Predecesores efectivos: los propios y los de sus ancestros, expandidos a nodos de trabajo
    val preds = HashMap<Long, Set<Long>>()
    for (n in sequence) {
        val set = HashSet<Long>()
        var cur: PlanNode? = n
        while (cur != null) {
            for (p in cur.predecessors) {
                for (w in workNodesUnder(tree, p)) {
                    if (w.id != n.id) set.add(w.id)
                }
            }
            cur = cur.parentId?.let { tree.byId[it] }
        }
        preds[n.id] = set
    }

    val pending = LinkedHashSet<Long>()
    for (n in sequence) {
        if (!isEffectivelyDone(n)) pending.add(n.id)
    }
    val order = ArrayList<PlanNode>()
    while (pending.isNotEmpty()) {
        var best: Long? = null
        for (id in pending) {
            val ready = preds[id].orEmpty().none { pending.contains(it) }
            if (ready && (best == null || index.getValue(id) < index.getValue(best))) best = id
        }
        val chosen = best ?: return null
        order.add(tree.byId.getValue(chosen))
        pending.remove(chosen)
    }
    return WorkOrder(order, preds)
}

// Reparte la secuencia en dias laborables a partir de start.
// Lanza NoWorkingDayException si no hay dias disponibles.
private fun allocate(
    sequence: List<PlanNode>,
    config: PlanConfig,
    start: Long,
    ignoreNotBefore: Set<Long>
): Allocation {
    val segments = ArrayList<Segment>()
    val zeroDates = HashMap<Long, Long>()
    val firstDay = nextWorkingDay(start, config)
    val capacity = config.dailyCapacityMinutes
    var day = firstDay
    var used = 0
    var lastWorkDay: Long? = null

    for (node in sequence) {
        val notBefore = if (ignoreNotBefore.contains(node.id)) null else node.notBeforeDate

        if (isZeroLength(node)) {
            if (!node.completed) {
                var base = lastWorkDay ?: firstDay
                if (notBefore != null && notBefore > base) base = nextWorkingDay(notBefore, config)
                zeroDates[node.id] = base
            }
            continue
        }

        val remaining = remainingMinutesOf(node)
        if (remaining <= 0) continue

        if (notBefore != null && day < notBefore) {
            day = nextWorkingDay(notBefore, config)
            used = 0
        }

        var rem = remaining
        while (rem > 0) {
            if (used >= capacity) {
                day = nextWorkingDay(day + 1, config)
                used = 0
            }
            val take = minOf(rem, capacity - used)
            segments.add(Segment(node.id, day, take))
            used += take
            rem -= take
            lastWorkDay = day
            if (day - firstDay > MAX_HORIZON_DAYS) throw NoWorkingDayException()
        }
    }
    return Allocation(segments, zeroDates)
}

// Reparto con la logica de "adelantar": los nodos marcados como pulled se colocan
// justo despues del ultimo nodo que ocupa hoy en el plan base, e ignoran su "no antes de".
private fun allocateAll(sequence: List<PlanNode>, config: PlanConfig, start: Long, today: Long): Allocation? {
    return try {
        val pulled = if (start <= today) {
            sequence.filter { it.pulledForwardDay == today && !isZeroLength(it) && remainingMinutesOf(it) > 0 }
        } else {
            emptyList()
        }
        if (pulled.isEmpty()) {
            allocate(sequence, config, start, emptySet())
        } else {
            val pulledIds = pulled.map { it.id }.toSet()
            val base = allocate(sequence, config, start, emptySet())
            val anchor = base.segments.lastOrNull { it.day == today && !pulledIds.contains(it.nodeId) }?.nodeId
            val reduced = sequence.filter { !pulledIds.contains(it.id) }
            val cut = if (anchor == null) 0 else reduced.indexOfFirst { it.id == anchor } + 1
            val seq2 = reduced.subList(0, cut) + pulled + reduced.subList(cut, reduced.size)
            allocate(seq2, config, start, pulledIds)
        }
    } catch (e: NoWorkingDayException) {
        null
    }
}

// Camino critico: desde el nodo que termina ultimo, retrocede por el predecesor que termina mas tarde
// (el anterior en ejecucion o una dependencia). Se para si el inicio viene de una restriccion "no antes de".
private fun criticalChain(
    order: List<PlanNode>,
    preds: Map<Long, Set<Long>>,
    schedules: Map<Long, NodeSchedule>,
    byId: Map<Long, PlanNode>
): Set<Long> {
    val ended = order.filter { schedules[it.id]?.endDay != null }
    if (ended.isEmpty()) return emptySet()
    val maxEnd = ended.maxOf { schedules.getValue(it.id).endDay!! }
    var current: PlanNode = ended.last { schedules.getValue(it.id).endDay == maxEnd }
    val pos = HashMap<Long, Int>()
    order.forEachIndexed { i, n -> pos[n.id] = i }
    val out = HashSet<Long>()
    while (true) {
        out.add(current.id)
        val start = schedules.getValue(current.id).startDay
        if (current.notBeforeDate != null && start == current.notBeforeDate) break
        val candidates = ArrayList<PlanNode>()
        val prev = (pos[current.id] ?: 0) - 1
        if (prev >= 0) candidates.add(order[prev])
        preds[current.id].orEmpty().forEach { pid ->
            val p = byId[pid]
            if (p != null) candidates.add(p)
        }
        val next = candidates
            .filter { schedules[it.id]?.endDay != null }
            .maxByOrNull { schedules.getValue(it.id).endDay!! }
            ?: break
        if (out.contains(next.id)) break
        current = next
    }
    return out
}

// Calcula el plan completo del proyecto desde max(startDate, today)
fun planProject(config: PlanConfig, nodes: List<PlanNode>, today: Long): ProjectPlan {
    val tree = PlanTree(nodes)
    val workOrder: WorkOrder? = orderWork(nodes)
    val cyclic = workOrder == null
    val orderedNodes: List<PlanNode> = workOrder?.order ?: emptyList()
    val sequence = workSequence(tree)
    val remainingTotal = sequence.sumOf { remainingMinutesOf(it).toLong() }
    val capacity = config.dailyCapacityMinutes
    val start = maxOf(config.startDate, today)
    val canSchedule = !config.paused && !cyclic && remainingTotal > 0L && capacity > 0 &&
        (config.workingDaysMask and 127) != 0
    val allocation: Allocation? = if (canSchedule) allocateAll(orderedNodes, config, start, today) else null

    val segs = allocation?.segments ?: emptyList()
    val zeroDates: Map<Long, Long> = allocation?.zeroDates ?: emptyMap()

    val counts = segs.groupingBy { it.nodeId }.eachCount()
    val seen = HashMap<Long, Int>()
    val parts = segs.map { s ->
        val idx = (seen[s.nodeId] ?: 0) + 1
        seen[s.nodeId] = idx
        PlanPart(s.nodeId, idx, counts.getValue(s.nodeId), s.day, s.minutes)
    }.sortedBy { it.day }

    val firstDayOf = HashMap<Long, Long>()
    val lastDayOf = HashMap<Long, Long>()
    for (s in segs) {
        if (!firstDayOf.containsKey(s.nodeId)) firstDayOf[s.nodeId] = s.day
        lastDayOf[s.nodeId] = s.day
    }

    val schedules = HashMap<Long, NodeSchedule>()
    fun scheduleOf(id: Long): NodeSchedule {
        val cached = schedules[id]
        if (cached != null) return cached
        val node = tree.byId.getValue(id)
        val done = isDoneNode(tree, id)
        val result = when {
            tree.isGroup(id) -> {
                val kids = tree.childrenOf(id).map { scheduleOf(it.id) }
                NodeSchedule(
                    id,
                    kids.mapNotNull { it.startDay }.minOrNull(),
                    kids.mapNotNull { it.endDay }.maxOrNull(),
                    done,
                    progressOf(leavesUnder(tree, id), done)
                )
            }
            isZeroLength(node) -> NodeSchedule(
                id,
                zeroDates[id],
                zeroDates[id],
                node.completed,
                if (node.completed) 100 else 0
            )
            else -> NodeSchedule(id, firstDayOf[id], lastDayOf[id], done, progressOf(listOf(node), done))
        }
        schedules[id] = result
        return result
    }
    val nodeMap: Map<Long, NodeSchedule> = nodes.associate { it.id to scheduleOf(it.id) }

    val critical: Set<Long> = if (workOrder == null || allocation == null) {
        emptySet()
    } else {
        criticalChain(orderedNodes, workOrder.preds, nodeMap, tree.byId)
    }

    val expectedEnd: Long? = (parts.map { it.day } + zeroDates.values).maxOrNull()
    val allWorkLeaves = sequence.filter { !isZeroLength(it) }
    val projectDone = tree.roots.isNotEmpty() && tree.roots.all { isDoneNode(tree, it.id) }
    val projectProgress = progressOf(allWorkLeaves, projectDone)

    val deadline = config.deadlineDate
    val status: String
    val marginDays: Int?
    val overdueDays: Int
    if (config.paused) {
        status = PlanStatus.PAUSADO
        marginDays = null
        overdueDays = 0
    } else if (cyclic) {
        status = PlanStatus.CICLO
        marginDays = null
        overdueDays = 0
    } else if (remainingTotal == 0L) {
        status = PlanStatus.SIN_PENDIENTE
        marginDays = null
        overdueDays = 0
    } else if (allocation == null || expectedEnd == null) {
        status = PlanStatus.SIN_CAPACIDAD
        marginDays = null
        overdueDays = 0
    } else if (deadline == null) {
        status = PlanStatus.SIN_DEADLINE
        marginDays = null
        overdueDays = 0
    } else {
        val m = (deadline - expectedEnd).toInt()
        marginDays = m
        overdueDays = if (m < 0) -m else 0
        status = if (m < 0) PlanStatus.ATRASADO else if (m <= 1) PlanStatus.JUSTO else PlanStatus.OK
    }

    return ProjectPlan(
        today = today,
        status = status,
        expectedEndDay = expectedEnd,
        marginDays = marginDays,
        overdueDays = overdueDays,
        progressPercent = projectProgress,
        remainingMinutes = remainingTotal.toInt(),
        nodes = nodeMap,
        parts = parts,
        executionOrder = orderedNodes.map { it.id },
        criticalNodeIds = critical
    )
}

// Candidata a adelantar: primera hoja pendiente que el plan pone en un dia futuro, cuyos
// predecesores estan todos completos, solo si hoy queda capacidad libre. Devuelve null si no hay.
fun findPullCandidate(config: PlanConfig, nodes: List<PlanNode>, today: Long): Long? {
    if (config.paused || config.startDate > today) return null
    val work = orderWork(nodes) ?: return null
    val plan = planProject(config, nodes, today)
    val spare = config.dailyCapacityMinutes - plan.parts.filter { it.day == today }.sumOf { it.minutes }
    if (spare <= 0) return null
    val doneIds = nodes.filter { isEffectivelyDone(it) }.map { it.id }.toSet()
    return work.order.firstOrNull { n ->
        !isZeroLength(n) &&
            remainingMinutesOf(n) > 0 &&
            n.pulledForwardDay != today &&
            (plan.nodes[n.id]?.startDay ?: -1L) > today &&
            work.preds[n.id].orEmpty().all { it in doneIds }
    }?.id
}
