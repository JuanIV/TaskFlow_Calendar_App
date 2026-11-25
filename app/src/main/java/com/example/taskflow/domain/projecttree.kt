package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

const val MIN_LEAF_MINUTES = 1
const val DEFAULT_LEAF_MINUTES = 60
const val MIN_CAPACITY_MINUTES = 15
const val MAX_CAPACITY_MINUTES = 1440

enum class OpResult {
    OK,
    EMPTY_NAME,
    INVALID_CAPACITY,
    INVALID_DAYS,
    INVALID_DEADLINE,
    EMPTY_TITLE,
    INVALID_MINUTES,
    PARENT_IS_MILESTONE,
    PARENT_HAS_PROGRESS,
    CYCLE,
    NOT_FOUND,
    IS_GROUP,
    INVALID_STATUS,
    NEEDS_CONFIRMATION
}

// Datos de un nodo que llegan desde el formulario
data class NodeDraft(
    val title: String,
    val notes: String?,
    val estimatedMinutes: Int,
    val isMilestone: Boolean,
    val priority: Int,
    val colorArgb: Int?,
    val notBeforeDate: Long?
)

// Datos de un proyecto que llegan desde el formulario
data class ProjectFormData(
    val name: String,
    val description: String?,
    val colorArgb: Int,
    val icon: String?,
    val startDate: Long,
    val deadlineDate: Long?,
    val capacityMinutes: Int,
    val workingDaysMask: Int,
    val excludedDays: List<Long>,
    val kind: String = ProjectKind.SIMPLE
)

// "1,5" -> 90, "2" -> 120. Devuelve null si no es un numero valido o es negativo.
fun parseHoursToMinutes(text: String): Int? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite() || value < 0.0) return null
    return Math.round(value * 60.0).toInt()
}

// 240 -> "4", 90 -> "1.5", 15 -> "0.25"
fun formatHours(minutes: Int): String =
    String.format(Locale.ROOT, "%.2f", minutes / 60.0).trimEnd('0').trimEnd('.')

fun epochDayToPickerMillis(day: Long): Long =
    LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

fun pickerMillisToEpochDay(ms: Long): Long =
    Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()

fun cleanNotes(notes: String?): String? = notes?.trim()?.takeIf { it.isNotEmpty() }

fun canTransitionProjectStatus(from: String, to: String): Boolean = when (from) {
    ProjectStatus.ACTIVE -> to == ProjectStatus.PAUSED || to == ProjectStatus.COMPLETED || to == ProjectStatus.ARCHIVED
    ProjectStatus.PAUSED -> to == ProjectStatus.ACTIVE || to == ProjectStatus.COMPLETED || to == ProjectStatus.ARCHIVED
    ProjectStatus.COMPLETED -> to == ProjectStatus.ACTIVE || to == ProjectStatus.ARCHIVED
    ProjectStatus.ARCHIVED -> to == ProjectStatus.ACTIVE
    else -> false
}

fun validateProject(
    name: String,
    capacityMinutes: Int,
    workingDaysMask: Int,
    startDate: Long,
    deadlineDate: Long?
): OpResult = when {
    name.isBlank() -> OpResult.EMPTY_NAME
    capacityMinutes < MIN_CAPACITY_MINUTES || capacityMinutes > MAX_CAPACITY_MINUTES -> OpResult.INVALID_CAPACITY
    (workingDaysMask and 127) == 0 -> OpResult.INVALID_DAYS
    deadlineDate != null && deadlineDate < startDate -> OpResult.INVALID_DEADLINE
    else -> OpResult.OK
}

// asLeaf = true cuando el nodo final sera una hoja (o un hito). Los grupos no validan minutos.
fun validateNodeDraft(draft: NodeDraft, asLeaf: Boolean): OpResult = when {
    draft.title.isBlank() -> OpResult.EMPTY_TITLE
    asLeaf && !draft.isMilestone && draft.estimatedMinutes < MIN_LEAF_MINUTES -> OpResult.INVALID_MINUTES
    else -> OpResult.OK
}

fun hasChildren(nodes: List<ProjectNodeEntity>, id: Long): Boolean = nodes.any { it.parentId == id }

// Hijos directos ordenados como en la vista (sortOrder, luego id)
fun childrenOfNode(nodes: List<ProjectNodeEntity>, parentId: Long?): List<ProjectNodeEntity> =
    nodes.filter { it.parentId == parentId }.sortedWith(compareBy<ProjectNodeEntity>({ it.sortOrder }, { it.id }))

// Ids del nodo y de todos sus descendientes
fun subtreeIds(nodes: List<ProjectNodeEntity>, rootId: Long): Set<Long> {
    val out = HashSet<Long>()
    val stack = ArrayDeque<Long>()
    stack.add(rootId)
    while (stack.isNotEmpty()) {
        val id = stack.removeLast()
        if (!out.add(id)) continue
        nodes.filter { it.parentId == id }.forEach { stack.add(it.id) }
    }
    return out
}

fun isNodeInProgress(node: ProjectNodeEntity): Boolean = node.completed || node.doneMinutes > 0

fun subtreeHasProgress(nodes: List<ProjectNodeEntity>, rootId: Long): Boolean {
    val ids = subtreeIds(nodes, rootId)
    return nodes.any { it.id in ids && isNodeInProgress(it) }
}

// Comprueba si el nodo puede recibir hijos
fun checkCanHaveChildren(nodes: List<ProjectNodeEntity>, parent: ProjectNodeEntity): OpResult = when {
    hasChildren(nodes, parent.id) -> OpResult.OK
    parent.isMilestone -> OpResult.PARENT_IS_MILESTONE
    isNodeInProgress(parent) -> OpResult.PARENT_HAS_PROGRESS
    else -> OpResult.OK
}

// Una hoja que recibe hijos deja de tener duracion propia
fun leafToGroup(node: ProjectNodeEntity): ProjectNodeEntity = node.copy(
    isMilestone = false,
    estimatedMinutes = 0,
    doneMinutes = 0,
    completed = false,
    completedAt = null,
    actualMinutes = null,
    pulledForwardDay = null
)

// Un grupo que pierde todos sus hijos vuelve a ser hoja con duracion por defecto
fun groupToLeaf(node: ProjectNodeEntity): ProjectNodeEntity = node.copy(
    isMilestone = false,
    estimatedMinutes = DEFAULT_LEAF_MINUTES,
    doneMinutes = 0,
    completed = false,
    completedAt = null,
    actualMinutes = null
)

// Convierte en hito (duracion 0). Si ya era hito, conserva su estado completado.
fun toMilestone(node: ProjectNodeEntity): ProjectNodeEntity =
    if (node.isMilestone) {
        node.copy(estimatedMinutes = 0, doneMinutes = 0)
    } else {
        node.copy(
            isMilestone = true,
            estimatedMinutes = 0,
            doneMinutes = 0,
            completed = false,
            completedAt = null,
            actualMinutes = null
        )
    }

// Cambia la duracion de una hoja respetando su progreso.
// Si estaba completada, sigue completada con la nueva duracion.
// Si el progreso supera la nueva duracion, queda completada.
fun resizeLeaf(node: ProjectNodeEntity, minutes: Int, nowMs: Long): ProjectNodeEntity {
    val wasCompletedLeaf = !node.isMilestone && node.completed
    val base = if (node.isMilestone) 0 else node.doneMinutes
    val newDone = if (wasCompletedLeaf) minutes else minOf(base, minutes)
    val done = newDone >= minutes
    return node.copy(
        isMilestone = false,
        estimatedMinutes = minutes,
        doneMinutes = newDone,
        completed = done,
        completedAt = if (done) (node.completedAt ?: nowMs) else null
    )
}

// Inserta nodeId en la posicion index de order (quitando antes cualquier aparicion previa)
fun insertAt(order: List<Long>, nodeId: Long, index: Int): List<Long> {
    val list = order.filter { it != nodeId }.toMutableList()
    list.add(index.coerceIn(0, list.size), nodeId)
    return list
}

// Destinos validos para mover un nodo: no es el nodo ni sus descendientes, no es hito,
// y si es hoja no tiene progreso.
fun moveTargetsFor(nodes: List<ProjectNodeEntity>, nodeId: Long): List<ProjectNodeEntity> {
    val excluded = subtreeIds(nodes, nodeId)
    return nodes
        .filter { it.id !in excluded && !it.isMilestone }
        .filter { hasChildren(nodes, it.id) || !isNodeInProgress(it) }
        .sortedBy { it.title.lowercase() }
}
