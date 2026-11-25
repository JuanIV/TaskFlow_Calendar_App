package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectCellAssignmentEntity
import com.example.taskflow.data.entities.ProjectDependencyEntity
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.data.entities.TaskEntity

// Dias que se materializan por adelantado en SIMPLE + DAILY
const val DAILY_WINDOW_DAYS = 30

// Tarea que un proyecto desea tener materializada en la tabla tasks
data class DesiredTask(
    val key: String,
    val projectId: Long,
    val nodeId: Long?,
    val title: String,
    val date: Long?,
    val estimatedMinutes: Int?,
    val priority: Int = 0,
    // false = la fecha la elige el usuario (modo NONE) y no se sobrescribe
    val managedDate: Boolean = true,
    // true = debe quedar completada (recordatorio diario avanzado con todo hecho)
    val completed: Boolean = false
)

data class ReconcileResult(
    val toInsert: List<DesiredTask>,
    val toUpdate: List<TaskEntity>,
    val toDelete: List<Long>
)

// ---------- Claves unicas ----------

fun projectPartKey(projectId: Long, nodeId: Long, day: Long): String = "P" + projectId + ":N" + nodeId + ":D" + day

fun projectCellKey(projectId: Long, nodeId: Long, day: Long): String = "P" + projectId + ":N" + nodeId + ":C" + day

fun projectDailyKey(projectId: Long, day: Long): String = "P" + projectId + ":DAY" + day

fun projectLooseKey(projectId: Long, nodeId: Long): String = "P" + projectId + ":N" + nodeId + ":L"

fun DesiredTask.toTaskEntity(nowMs: Long): TaskEntity = TaskEntity(
    title = title,
    date = date,
    isCompleted = completed,
    completedAt = if (completed) nowMs else null,
    createdAt = nowMs,
    priority = priority,
    estimatedMinutes = estimatedMinutes,
    projectId = projectId,
    projectNodeId = nodeId,
    projectKey = key
)

// Minutos de partes atrasadas sin completar, por nodo. El planificador los descuenta
// para no duplicarlos al repartir de nuevo desde hoy.
fun retainedMinutesByNode(existing: List<TaskEntity>, today: Long): Map<Long, Int> {
    val out = HashMap<Long, Int>()
    for (t in existing) {
        val node = t.projectNodeId ?: continue
        val d = t.date ?: continue
        if (t.isCompleted || t.deletedAt != null || d >= today) continue
        out[node] = (out[node] ?: 0) + (t.estimatedMinutes ?: 0)
    }
    return out
}

// Tareas que el proyecto desea tener segun tipo (kind) y modo de seguimiento.
// Un proyecto no activo, borrado o plantilla no desea nada.
fun desiredProjectTasks(
    project: ProjectEntity,
    nodes: List<ProjectNodeEntity>,
    dependencies: List<ProjectDependencyEntity>,
    excludedDays: Set<Long>,
    assignments: List<ProjectCellAssignmentEntity>,
    retainedMinutes: Map<Long, Int>,
    today: Long,
    dailyPrefix: String
): List<DesiredTask> {
    if (project.deletedAt != null || project.isTemplate || project.status != ProjectStatus.ACTIVE) {
        return emptyList()
    }
    val advanced = effectiveProjectKind(project.kind) == ProjectKind.ADVANCED
    return when (effectiveTrackingMode(project.trackingMode)) {
        TrackingMode.DAILY ->
            if (advanced) advancedDaily(project, nodes, assignments, today, dailyPrefix)
            else simpleDaily(project, nodes, excludedDays, today, dailyPrefix)
        TrackingMode.NONE -> looseTasks(project, nodes, advanced)
        else ->
            if (advanced) advancedCells(project, nodes, assignments, today)
            else simpleParts(project, nodes, dependencies, excludedDays, retainedMinutes, today)
    }
}

private fun simpleParts(
    project: ProjectEntity,
    nodes: List<ProjectNodeEntity>,
    dependencies: List<ProjectDependencyEntity>,
    excludedDays: Set<Long>,
    retained: Map<Long, Int>,
    today: Long
): List<DesiredTask> {
    val titles = nodes.associate { it.id to it.title }
    val priorities = nodes.associate { it.id to it.priority }
    val preds = dependencies.groupBy({ it.successorId }, { it.predecessorId })
    val planNodes = nodes.map { n ->
        val extra = maxOf(0, retained[n.id] ?: 0)
        val done = if (n.isMilestone || n.estimatedMinutes <= 0) {
            n.doneMinutes
        } else {
            minOf(n.estimatedMinutes, n.doneMinutes + extra)
        }
        PlanNode(
            id = n.id,
            parentId = n.parentId,
            sortOrder = n.sortOrder,
            isMilestone = n.isMilestone,
            estimatedMinutes = n.estimatedMinutes,
            doneMinutes = done,
            completed = n.completed,
            notBeforeDate = n.notBeforeDate,
            pulledForwardDay = n.pulledForwardDay,
            predecessors = preds[n.id].orEmpty()
        )
    }
    val config = PlanConfig(
        startDate = project.startDate,
        deadlineDate = project.deadlineDate,
        dailyCapacityMinutes = project.dailyCapacityMinutes,
        workingDaysMask = project.workingDaysMask,
        excludedDays = excludedDays,
        paused = false
    )
    val plan = planProject(config, planNodes, today)
    return plan.parts.map { part ->
        DesiredTask(
            key = projectPartKey(project.id, part.nodeId, part.day),
            projectId = project.id,
            nodeId = part.nodeId,
            title = projectTodayLabel(project.name, part, titles[part.nodeId].orEmpty()),
            date = part.day,
            estimatedMinutes = part.minutes,
            priority = priorities[part.nodeId] ?: 0
        )
    }
}

private fun simpleDaily(
    project: ProjectEntity,
    nodes: List<ProjectNodeEntity>,
    excludedDays: Set<Long>,
    today: Long,
    dailyPrefix: String
): List<DesiredTask> {
    val leaves = leafNodeIds(nodes)
    val hasOpen = nodes.isEmpty() || nodes.any { it.id in leaves && !it.completed }
    if (!hasOpen) return emptyList()
    val config = PlanConfig(
        startDate = project.startDate,
        deadlineDate = project.deadlineDate,
        dailyCapacityMinutes = project.dailyCapacityMinutes,
        workingDaysMask = project.workingDaysMask,
        excludedDays = excludedDays,
        paused = false
    )
    val first = maxOf(today, project.startDate)
    val deadline = project.deadlineDate
    val out = ArrayList<DesiredTask>()
    for (i in 0 until DAILY_WINDOW_DAYS) {
        val day = first + i
        if (deadline != null && day > deadline) break
        if (!isWorkingDay(day, config)) continue
        out.add(
            DesiredTask(
                key = projectDailyKey(project.id, day),
                projectId = project.id,
                nodeId = null,
                title = dailyPrefix + " " + project.name,
                date = day,
                estimatedMinutes = null
            )
        )
    }
    return out
}

private fun advancedCells(
    project: ProjectEntity,
    nodes: List<ProjectNodeEntity>,
    assignments: List<ProjectCellAssignmentEntity>,
    today: Long
): List<DesiredTask> {
    val leaves = leafNodeIds(nodes)
    val byId = nodes.associateBy { it.id }
    return assignments
        .filter { it.epochDay >= today && it.nodeId in leaves }
        .mapNotNull { a ->
            val n = byId[a.nodeId]
            if (n == null || n.completed) {
                null
            } else {
                DesiredTask(
                    key = projectCellKey(project.id, n.id, a.epochDay),
                    projectId = project.id,
                    nodeId = n.id,
                    title = project.name + ": " + n.title,
                    date = a.epochDay,
                    estimatedMinutes = null,
                    priority = n.priority
                )
            }
        }
        .sortedWith(compareBy<DesiredTask>({ it.date }, { it.key }))
}

private fun advancedDaily(
    project: ProjectEntity,
    nodes: List<ProjectNodeEntity>,
    assignments: List<ProjectCellAssignmentEntity>,
    today: Long,
    dailyPrefix: String
): List<DesiredTask> {
    val leaves = leafNodeIds(nodes)
    val byId = nodes.associateBy { it.id }
    val byDay = assignments
        .filter { it.epochDay >= today && it.nodeId in leaves }
        .groupBy { it.epochDay }
    val out = ArrayList<DesiredTask>()
    for (day in byDay.keys.sorted()) {
        val cells = byDay.getValue(day)
        val allDone = cells.all { byId[it.nodeId]?.completed == true }
        // Un dia futuro con todo hecho ya no necesita recordatorio
        if (allDone && day != today) continue
        out.add(
            DesiredTask(
                key = projectDailyKey(project.id, day),
                projectId = project.id,
                nodeId = null,
                title = dailyPrefix + " " + project.name,
                date = day,
                estimatedMinutes = null,
                completed = allDone
            )
        )
    }
    return out
}

private fun looseTasks(
    project: ProjectEntity,
    nodes: List<ProjectNodeEntity>,
    advanced: Boolean
): List<DesiredTask> {
    val leaves = leafNodeIds(nodes)
    return nodes
        .filter { it.id in leaves && !it.completed }
        .sortedWith(compareBy<ProjectNodeEntity>({ it.sortOrder }, { it.id }))
        .map { n ->
            DesiredTask(
                key = projectLooseKey(project.id, n.id),
                projectId = project.id,
                nodeId = n.id,
                title = project.name + ": " + n.title,
                date = null,
                estimatedMinutes = if (!advanced && n.estimatedMinutes > 0) n.estimatedMinutes else null,
                priority = n.priority,
                managedDate = false
            )
        }
}

// Compara lo deseado con lo existente (solo tareas del mismo proyecto con projectKey).
// Reglas:
// - Las completadas no se tocan y bloquean la reinsercion de su clave.
// - Las pendientes deseadas se actualizan en sitio (titulo, fecha si esta gestionada, minutos).
// - Las pendientes no deseadas: sin fecha o de un nodo completado se completan (fecha <= hoy) o se borran;
//   de un nodo que ya no existe se borran; futuras se borran; las atrasadas se conservan
//   si keepPastPending y su nodo sigue abierto, y si no se borran solo cuando su dia ya esta cerrado.
// - Las filas con deletedAt se borran fisicamente.
// El orden de aplicacion debe ser: borrar, actualizar, insertar.
fun reconcileProjectTasks(
    desired: List<DesiredTask>,
    existing: List<TaskEntity>,
    today: Long,
    closedUpTo: Long,
    openNodeIds: Set<Long>,
    completedNodeIds: Set<Long>,
    keepPastPending: Boolean,
    nowMs: Long
): ReconcileResult {
    val desiredByKey = desired.associateBy { it.key }
    val toUpdate = ArrayList<TaskEntity>()
    val toDelete = ArrayList<Long>()
    val present = HashSet<String>()

    for (e in existing) {
        val key = e.projectKey ?: continue
        if (e.deletedAt != null) {
            toDelete.add(e.id)
            continue
        }
        present.add(key)
        if (e.isCompleted) continue

        val d = desiredByKey[key]
        if (d != null) {
            if (d.completed) {
                toUpdate.add(e.copy(isCompleted = true, completedAt = nowMs))
                continue
            }
            var upd = e
            if (upd.title != d.title) upd = upd.copy(title = d.title)
            if (d.managedDate && upd.date != d.date) upd = upd.copy(date = d.date)
            val est = d.estimatedMinutes
            if (est != null && upd.estimatedMinutes != est) upd = upd.copy(estimatedMinutes = est)
            if (upd != e) toUpdate.add(upd)
            continue
        }

        val date = e.date
        val nodeId = e.projectNodeId
        if (nodeId != null && nodeId in completedNodeIds) {
            if (date == null || date <= today) {
                toUpdate.add(e.copy(isCompleted = true, completedAt = nowMs))
            } else {
                toDelete.add(e.id)
            }
            continue
        }
        if (nodeId != null && nodeId !in openNodeIds) {
            toDelete.add(e.id)
            continue
        }
        if (date == null || date >= today) {
            toDelete.add(e.id)
            continue
        }
        // Atrasada y pendiente
        if (keepPastPending && nodeId != null) continue
        if (date <= closedUpTo) toDelete.add(e.id)
    }

    val toInsert = desired.filter { it.key !in present }
    return ReconcileResult(toInsert, toUpdate, toDelete)
}
