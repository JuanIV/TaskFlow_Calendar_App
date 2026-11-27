package com.example.taskflow.data.repositories

import androidx.room.withTransaction
import com.example.taskflow.data.daos.ProjectDao
import com.example.taskflow.data.daos.ProjectNodeDao
import com.example.taskflow.data.db.AppDatabase
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectExcludedDayEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.data.entities.ProjectDependencyEntity
import com.example.taskflow.data.entities.ProjectDailyLogEntity
import com.example.taskflow.data.entities.ProjectCellAssignmentEntity
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.isProjectPartKey
import com.example.taskflow.domain.isProjectCellKey
import com.example.taskflow.domain.ProjectKind
import com.example.taskflow.domain.isValidProjectKind
import com.example.taskflow.domain.effectiveProjectKind
import com.example.taskflow.domain.effectiveTrackingMode
import com.example.taskflow.domain.isValidTrackingMode
import com.example.taskflow.domain.advancedProjectPlan
import com.example.taskflow.domain.logicalDayOfMillis
import com.example.taskflow.domain.DayProjectTotals
import com.example.taskflow.domain.DEFAULT_LEAF_MINUTES
import com.example.taskflow.domain.MIN_LEAF_MINUTES
import com.example.taskflow.domain.NodeDraft
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.PlanConfig
import com.example.taskflow.domain.PlanNode
import com.example.taskflow.domain.ProjectFormData
import com.example.taskflow.domain.ProjectPlan
import com.example.taskflow.domain.ProjectStatus
import com.example.taskflow.domain.applyPartDone
import com.example.taskflow.domain.canTransitionProjectStatus
import com.example.taskflow.domain.checkCanHaveChildren
import com.example.taskflow.domain.childrenOfNode
import com.example.taskflow.domain.clampedDone
import com.example.taskflow.domain.cleanNotes
import com.example.taskflow.domain.groupToLeaf
import com.example.taskflow.domain.hasChildren
import com.example.taskflow.domain.insertAt
import com.example.taskflow.domain.isZeroLength
import com.example.taskflow.domain.leafToGroup
import com.example.taskflow.domain.mergeDailyLog
import com.example.taskflow.domain.orderWork
import com.example.taskflow.domain.planProject
import com.example.taskflow.domain.preorderNodeIds
import com.example.taskflow.domain.projectCsvRows
import com.example.taskflow.domain.todayParts
import com.example.taskflow.domain.resizeLeaf
import com.example.taskflow.domain.subtreeHasProgress
import com.example.taskflow.domain.subtreeIds
import com.example.taskflow.domain.toMilestone
import com.example.taskflow.domain.validateNodeDraft
import com.example.taskflow.domain.validateProject
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

fun ProjectNodeEntity.toPlanNode(predecessors: List<Long> = emptyList()): PlanNode = PlanNode(
    id = id,
    parentId = parentId,
    sortOrder = sortOrder,
    isMilestone = isMilestone,
    estimatedMinutes = estimatedMinutes,
    doneMinutes = doneMinutes,
    completed = completed,
    notBeforeDate = notBeforeDate,
    pulledForwardDay = pulledForwardDay,
    predecessors = predecessors
)

// Convierte nodos y dependencias en PlanNode con sus predecesores
fun buildPlanNodes(nodes: List<ProjectNodeEntity>, deps: List<ProjectDependencyEntity>): List<PlanNode> {
    val predsByNode = deps.groupBy({ it.successorId }, { it.predecessorId })
    return nodes.map { it.toPlanNode(predsByNode[it.id].orEmpty()) }
}

// Un proyecto no ACTIVE no genera trabajo planificado
fun ProjectEntity.toPlanConfig(excludedDays: Set<Long>): PlanConfig = PlanConfig(
    startDate = startDate,
    deadlineDate = deadlineDate,
    dailyCapacityMinutes = dailyCapacityMinutes,
    workingDaysMask = workingDaysMask,
    excludedDays = excludedDays,
    paused = status != ProjectStatus.ACTIVE
)

@Singleton
class ProjectRepository @Inject constructor(
    private val database: AppDatabase,
    private val projectDao: ProjectDao,
    private val nodeDao: ProjectNodeDao,
    private val taskSync: ProjectTaskSync
) {
    // Si la operacion fue OK, re-sincroniza las tareas materializadas del proyecto
    private suspend fun syncIfOk(result: OpResult, projectId: Long): OpResult {
        if (result == OpResult.OK && projectId != 0L) taskSync.syncProject(projectId)
        return result
    }

    // Plan de un proyecto. Los avanzados no pasan por el planificador.
    private fun planFor(
        project: ProjectEntity,
        entities: List<ProjectNodeEntity>,
        deps: List<ProjectDependencyEntity>,
        excluded: Set<Long>,
        today: Long
    ): ProjectPlan =
        if (effectiveProjectKind(project.kind) == ProjectKind.ADVANCED) {
            advancedProjectPlan(today, entities)
        } else {
            planProject(project.toPlanConfig(excluded), buildPlanNodes(entities, deps), today)
        }

    // ---------- Observacion ----------

    fun observeActiveProjects(): Flow<List<ProjectEntity>> = projectDao.getActiveProjects()

    fun observeTemplates(): Flow<List<ProjectEntity>> = projectDao.getTemplates()

    fun observeProject(projectId: Long): Flow<ProjectEntity?> = projectDao.observeProject(projectId)

    fun observeNodes(projectId: Long): Flow<List<ProjectNodeEntity>> = nodeDao.observeNodes(projectId)

    fun observeAllNodes(): Flow<List<ProjectNodeEntity>> = nodeDao.observeAllNodes()

    fun observeAllExcludedDays(): Flow<List<ProjectExcludedDayEntity>> = projectDao.observeAllExcludedDays()

    // ---------- Lectura ----------

    suspend fun getProject(projectId: Long): ProjectEntity? = projectDao.getProjectById(projectId)

    suspend fun getNodes(projectId: Long): List<ProjectNodeEntity> = nodeDao.getNodesSync(projectId)

    suspend fun getExcludedDays(projectId: Long): List<Long> = projectDao.getExcludedDays(projectId)

    // ---------- Proyecto ----------

    // Crea (existing == null) o actualiza un proyecto, con sus dias excluidos
    suspend fun saveProject(existing: ProjectEntity?, form: ProjectFormData, nowMs: Long): OpResult {
        val check = validateProject(form.name, form.capacityMinutes, form.workingDaysMask, form.startDate, form.deadlineDate)
        if (check != OpResult.OK) return check
        val name = form.name.trim()
        val description = form.description?.trim()?.takeIf { it.isNotEmpty() }
        val excluded = form.excludedDays.distinct()
        var savedId = existing?.id ?: 0L
        database.withTransaction<Unit> {
            if (existing == null) {
                val id = projectDao.insertProject(
                    ProjectEntity(
                        name = name,
                        description = description,
                        colorArgb = form.colorArgb,
                        icon = form.icon,
                        startDate = form.startDate,
                        deadlineDate = form.deadlineDate,
                        dailyCapacityMinutes = form.capacityMinutes,
                        workingDaysMask = form.workingDaysMask,
                        status = ProjectStatus.ACTIVE,
                        isTemplate = false,
                        createdAt = nowMs,
                        kind = form.kind,
                        trackingMode = effectiveTrackingMode(form.trackingMode)
                    )
                )
                savedId = id
                if (excluded.isNotEmpty()) {
                    projectDao.insertExcludedDays(excluded.map { ProjectExcludedDayEntity(id, it) })
                }
            } else {
                // Nota: cambiar de ADVANCED a SIMPLE conserva las asignaciones; no se usan hasta volver a ADVANCED.
                projectDao.updateProject(
                    existing.copy(
                        name = name,
                        description = description,
                        colorArgb = form.colorArgb,
                        icon = form.icon,
                        startDate = form.startDate,
                        deadlineDate = form.deadlineDate,
                        dailyCapacityMinutes = form.capacityMinutes,
                        workingDaysMask = form.workingDaysMask,
                        kind = form.kind,
                        trackingMode = effectiveTrackingMode(form.trackingMode)
                    )
                )
                projectDao.deleteExcludedDays(existing.id)
                if (excluded.isNotEmpty()) {
                    projectDao.insertExcludedDays(excluded.map { ProjectExcludedDayEntity(existing.id, it) })
                }
            }
        }
        taskSync.syncProject(savedId)
        return OpResult.OK
    }

    suspend fun softDeleteProject(projectId: Long, nowMs: Long) {
        projectDao.softDeleteProject(projectId, nowMs)
        taskSync.syncProject(projectId)
    }

    suspend fun restoreProject(projectId: Long) {
        projectDao.restoreProject(projectId)
        taskSync.syncProject(projectId)
    }

    suspend fun permanentlyDeleteProject(projectId: Long) {
        projectDao.permanentlyDeleteProject(projectId)
        taskSync.purgeProject(projectId)
    }

    // Cambia el estado respetando las transiciones permitidas
    suspend fun setStatus(projectId: Long, newStatus: String): OpResult {
        val project = projectDao.getProjectById(projectId) ?: return OpResult.NOT_FOUND
        if (!canTransitionProjectStatus(project.status, newStatus)) return OpResult.INVALID_STATUS
        projectDao.updateProject(project.copy(status = newStatus))
        taskSync.syncProject(projectId)
        return OpResult.OK
    }

    // Copia un proyecto (RF-7). Reinicia progreso y adelantos. Devuelve el id nuevo o null.
    // Las celdas de un proyecto avanzado se copian desplazadas (newStartDate - startDate del origen).
    suspend fun duplicateProject(
        sourceId: Long,
        newName: String,
        asTemplate: Boolean,
        newStartDate: Long,
        nowMs: Long
    ): Long? {
        val name = newName.trim()
        if (name.isEmpty()) return null
        val newId = database.withTransaction<Long?> {
            val source = projectDao.getProjectById(sourceId) ?: return@withTransaction null
            val excluded = projectDao.getExcludedDays(sourceId)
            val createdId = projectDao.insertProject(
                source.copy(
                    id = 0,
                    name = name,
                    isTemplate = asTemplate,
                    startDate = newStartDate,
                    status = ProjectStatus.ACTIVE,
                    createdAt = nowMs,
                    deletedAt = null
                )
            )
            if (excluded.isNotEmpty()) {
                projectDao.insertExcludedDays(excluded.map { ProjectExcludedDayEntity(createdId, it) })
            }
            val entities = nodeDao.getNodesSync(sourceId)
            val byId = entities.associateBy { it.id }
            val order = preorderNodeIds(entities.map { it.toPlanNode() })
            val newIds = HashMap<Long, Long>()
            for (oldId in order) {
                val old = byId.getValue(oldId)
                val newNodeId = nodeDao.insertNode(
                    old.copy(
                        id = 0,
                        projectId = createdId,
                        parentId = old.parentId?.let { newIds[it] },
                        doneMinutes = 0,
                        completed = false,
                        completedAt = null,
                        actualMinutes = null,
                        pulledForwardDay = null,
                        createdAt = nowMs
                    )
                )
                newIds[oldId] = newNodeId
            }
            val depDao = database.projectDependencyDao()
            for (dep in depDao.getForProject(sourceId)) {
                val p = newIds[dep.predecessorId]
                val s = newIds[dep.successorId]
                if (p != null && s != null) depDao.insert(ProjectDependencyEntity(p, s))
            }
            val cellDao = database.projectCellDao()
            val delta = newStartDate - source.startDate
            val newCells = cellDao.getForProject(sourceId).mapNotNull { a ->
                val mapped = newIds[a.nodeId]
                if (mapped == null) null else ProjectCellAssignmentEntity(mapped, a.epochDay + delta)
            }
            if (newCells.isNotEmpty()) cellDao.insertAll(newCells)
            createdId
        }
        if (newId != null) taskSync.syncProject(newId)
        return newId
    }

    // ---------- Arbol ----------

    private fun newNodeEntity(projectId: Long, parentId: Long?, draft: NodeDraft, sortOrder: Int, nowMs: Long) =
        ProjectNodeEntity(
            projectId = projectId,
            parentId = parentId,
            title = draft.title.trim(),
            notes = cleanNotes(draft.notes),
            sortOrder = sortOrder,
            isMilestone = draft.isMilestone,
            estimatedMinutes = if (draft.isMilestone) 0 else draft.estimatedMinutes,
            doneMinutes = 0,
            completed = false,
            priority = draft.priority,
            colorArgb = draft.colorArgb,
            notBeforeDate = draft.notBeforeDate,
            createdAt = nowMs
        )

    // Anade un nodo al final de los hijos de parentId (null = raiz)
    suspend fun addNode(projectId: Long, parentId: Long?, draft: NodeDraft, nowMs: Long): OpResult {
        val r = database.withTransaction<OpResult> {
            val nodes = nodeDao.getNodesSync(projectId)
            val parent: ProjectNodeEntity? = if (parentId == null) null else nodes.firstOrNull { it.id == parentId }
            if (parentId != null && parent == null) return@withTransaction OpResult.NOT_FOUND
            if (parent != null) {
                val parentCheck = checkCanHaveChildren(nodes, parent)
                if (parentCheck != OpResult.OK) return@withTransaction parentCheck
            }
            val draftCheck = validateNodeDraft(draft, true)
            if (draftCheck != OpResult.OK) return@withTransaction draftCheck
            if (parent != null && !hasChildren(nodes, parent.id)) {
                nodeDao.updateNode(leafToGroup(parent))
                database.projectCellDao().deleteForNode(parent.id)
            }
            val nextOrder = (childrenOfNode(nodes, parentId).maxOfOrNull { it.sortOrder } ?: -1) + 1
            nodeDao.insertNode(newNodeEntity(projectId, parentId, draft, nextOrder, nowMs))
            OpResult.OK
        }
        return syncIfOk(r, projectId)
    }

    // Edita un nodo. En grupos se ignoran la duracion y la marca de hito.
    suspend fun updateNode(nodeId: Long, draft: NodeDraft, nowMs: Long): OpResult {
        var pid = 0L
        val r = database.withTransaction<OpResult> {
            val current = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
            pid = current.projectId
            val nodes = nodeDao.getNodesSync(current.projectId)
            val isGroup = hasChildren(nodes, nodeId)
            val check = validateNodeDraft(draft, !isGroup)
            if (check != OpResult.OK) return@withTransaction check
            var updated = current.copy(
                title = draft.title.trim(),
                notes = cleanNotes(draft.notes),
                priority = draft.priority,
                colorArgb = draft.colorArgb,
                notBeforeDate = draft.notBeforeDate
            )
            if (!isGroup) {
                updated = if (draft.isMilestone) {
                    toMilestone(updated)
                } else {
                    resizeLeaf(updated, draft.estimatedMinutes, nowMs)
                }
            }
            nodeDao.updateNode(updated)
            OpResult.OK
        }
        return syncIfOk(r, pid)
    }

    // Borra un nodo y sus descendientes. Si el padre se queda sin hijos, vuelve a ser hoja.
    suspend fun deleteNode(nodeId: Long, confirmed: Boolean): OpResult {
        var pid = 0L
        val r = database.withTransaction<OpResult> {
            val target = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
            pid = target.projectId
            val nodes = nodeDao.getNodesSync(target.projectId)
            if (!confirmed && subtreeHasProgress(nodes, nodeId)) return@withTransaction OpResult.NEEDS_CONFIRMATION
            val parentId = target.parentId
            nodeDao.deleteNode(nodeId)
            if (parentId != null) {
                val parent = nodes.firstOrNull { it.id == parentId }
                val parentStillHasChildren = nodes.any { it.parentId == parentId && it.id != nodeId }
                if (parent != null && !parentStillHasChildren) {
                    nodeDao.updateNode(groupToLeaf(parent))
                }
            }
            OpResult.OK
        }
        return syncIfOk(r, pid)
    }

    // Mueve un nodo a otro padre (null = raiz) en la posicion newIndex (se recorta al rango valido)
    suspend fun moveNode(nodeId: Long, newParentId: Long?, newIndex: Int): OpResult {
        var pid = 0L
        val r = database.withTransaction<OpResult> {
            val target = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
            pid = target.projectId
            val nodes = nodeDao.getNodesSync(target.projectId)
            if (newParentId != null && (newParentId == nodeId || subtreeIds(nodes, nodeId).contains(newParentId))) {
                return@withTransaction OpResult.CYCLE
            }
            val newParent: ProjectNodeEntity? = if (newParentId == null) null else nodes.firstOrNull { it.id == newParentId }
            if (newParentId != null && newParent == null) return@withTransaction OpResult.NOT_FOUND
            val changed = LinkedHashMap<Long, ProjectNodeEntity>()
            if (newParent != null && !hasChildren(nodes, newParent.id)) {
                val check = checkCanHaveChildren(nodes, newParent)
                if (check != OpResult.OK) return@withTransaction check
                changed[newParent.id] = leafToGroup(newParent)
                database.projectCellDao().deleteForNode(newParent.id)
            }
            val oldParentId = target.parentId
            if (oldParentId != null && oldParentId != newParentId) {
                val oldParent = nodes.firstOrNull { it.id == oldParentId }
                val oldStillHasChildren = nodes.any { it.parentId == oldParentId && it.id != nodeId }
                if (oldParent != null && !oldStillHasChildren) {
                    changed[oldParentId] = groupToLeaf(oldParent)
                }
            }
            val siblingIds = childrenOfNode(nodes, newParentId).map { it.id }.filter { it != nodeId }
            val ordered = insertAt(siblingIds, nodeId, newIndex)
            ordered.forEachIndexed { position, id ->
                val base = if (id == nodeId) target else nodes.first { it.id == id }
                changed[id] = base.copy(parentId = newParentId, sortOrder = position)
            }
            changed.values.forEach { nodeDao.updateNode(it) }
            OpResult.OK
        }
        return syncIfOk(r, pid)
    }

    // Completa o reabre una hoja o un hito. Sobre un grupo devuelve IS_GROUP.
    suspend fun setNodeCompleted(nodeId: Long, completed: Boolean, actualMinutes: Int?, nowMs: Long, reopenSweep: Boolean = true): OpResult {
        var pid = 0L
        val r = database.withTransaction<OpResult> {
            val node = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
            pid = node.projectId
            val nodes = nodeDao.getNodesSync(node.projectId)
            if (hasChildren(nodes, nodeId)) return@withTransaction OpResult.IS_GROUP
            val updated = when {
                node.isMilestone -> node.copy(completed = completed, completedAt = if (completed) nowMs else null)
                completed -> node.copy(
                    doneMinutes = node.estimatedMinutes,
                    completed = true,
                    completedAt = nowMs,
                    actualMinutes = actualMinutes
                )
                else -> node.copy(doneMinutes = 0, completed = false, completedAt = null, actualMinutes = null)
            }
            nodeDao.updateNode(updated)
            OpResult.OK
        }
        if (r == OpResult.OK && pid != 0L) {
            taskSync.syncProject(pid, if (completed || !reopenSweep) null else nodeId)
        }
        return r
    }

    // ---------- Operaciones de 1a (sin cambios de comportamiento) ----------

    // Registra minutos trabajados en una hoja. Si queda completa, marca completed.
    // No sincroniza: lo hacen quienes la llaman.
    private suspend fun markPartDone(nodeId: Long, minutes: Int, nowMs: Long) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        if (isZeroLength(entity.toPlanNode())) return
        val updated = applyPartDone(entity.toPlanNode(), minutes)
        nodeDao.updateNode(
            entity.copy(
                doneMinutes = updated.doneMinutes,
                completed = updated.completed,
                completedAt = if (updated.completed) nowMs else null
            )
        )
    }

    // Completa la parte de hoy de una hoja y registra los minutos en el log del dia.
    suspend fun completeTodayPart(nodeId: Long, minutes: Int, nowMs: Long, today: Long) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        if (isZeroLength(entity.toPlanNode())) return
        val remaining = maxOf(0, entity.estimatedMinutes - entity.doneMinutes)
        val done = minOf(minutes, remaining)
        if (minutes >= remaining) {
            nodeDao.updateNode(
                entity.copy(
                    doneMinutes = entity.estimatedMinutes,
                    completed = true,
                    completedAt = nowMs
                )
            )
        } else {
            markPartDone(nodeId, minutes, nowMs)
        }
        val log = database.projectDailyLogDao()
        val existing = log.getForNodeAndDay(nodeId, today)
        log.upsert(mergeDailyLog(existing, nodeId, today, existing?.plannedMinutes ?: minutes, (existing?.doneMinutes ?: 0) + done))
        taskSync.syncProject(entity.projectId)
    }

    // Reabre una hoja SIMPLE descontando los minutos de una parte que el usuario desmarca.
    suspend fun reopenPart(nodeId: Long, minutes: Int) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        if (isZeroLength(entity.toPlanNode())) return
        val wasCompleted = entity.completed
        nodeDao.updateNode(
            entity.copy(
                doneMinutes = maxOf(0, entity.doneMinutes - minutes),
                completed = false,
                completedAt = null,
                actualMinutes = null
            )
        )
        taskSync.syncProject(entity.projectId, if (wasCompleted) nodeId else null)
    }

    // Propaga a su nodo el completado o la reapertura de una tarea materializada por un proyecto.
    // Partes (SIMPLE dinamico): suman o restan minutos. Celdas (avanzado) y sueltas: completan o reabren la hoja.
    // Recordatorios diarios (sin nodo): no afectan a ningun nodo.
    suspend fun onProjectTaskToggled(task: TaskEntity, completed: Boolean, nowMs: Long, today: Long) {
        val key = task.projectKey ?: return
        val nodeId = task.projectNodeId ?: return
        if (isProjectPartKey(key)) {
            val minutes = task.estimatedMinutes ?: 0
            if (completed) completeTodayPart(nodeId, minutes, nowMs, today) else reopenPart(nodeId, minutes)
        } else if (isProjectCellKey(key)) {
            onCellTaskToggled(nodeId, completed, nowMs)
        } else {
            setNodeCompleted(nodeId, completed, null, nowMs)
        }
    }

    // Tarea de una celda de un proyecto avanzado: completarla es haber trabajado ese dia en el objetivo.
    // El objetivo solo se completa cuando no queda ninguna tarea de celda pendiente.
    // Reabrir una tarea reabre el objetivo si estaba completado (sin borrar el resto de tareas hechas).
    private suspend fun onCellTaskToggled(nodeId: Long, completed: Boolean, nowMs: Long) {
        val node = nodeDao.getNodeById(nodeId) ?: return
        if (completed) {
            if (node.completed) return
            val cellTasks = database.taskDao().getProjectTasksSync(node.projectId).filter {
                val k = it.projectKey
                it.projectNodeId == nodeId && it.deletedAt == null && k != null && isProjectCellKey(k)
            }
            if (cellTasks.none { !it.isCompleted }) setNodeCompleted(nodeId, true, null, nowMs)
        } else if (node.completed) {
            setNodeCompleted(nodeId, false, null, nowMs, false)
        }
    }

    // Adelantar a hoy (RF-19)
    suspend fun setPulledForward(nodeId: Long, today: Long) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        nodeDao.updateNode(entity.copy(pulledForwardDay = today))
        taskSync.syncProject(entity.projectId)
    }

    // Calcula el plan del proyecto. today debe venir de logicalDateNow(dayStartHour).
    suspend fun loadPlan(projectId: Long, today: Long): ProjectPlan? {
        val project = projectDao.getProjectById(projectId) ?: return null
        val excluded = projectDao.getExcludedDays(projectId).toSet()
        val entities = nodeDao.getNodesSync(projectId)
        val deps = database.projectDependencyDao().getForProject(projectId)
        return planFor(project, entities, deps, excluded, today)
    }

    // Proyectos simples activos con su plan de hoy. Los avanzados no tienen plan.
    suspend fun getActiveProjectPlansSync(today: Long): List<Pair<ProjectEntity, ProjectPlan>> {
        val projects = projectDao.getActiveProjectsSync()
            .filter { effectiveProjectKind(it.kind) != ProjectKind.ADVANCED }
        return projects.map { project ->
            val excluded = projectDao.getExcludedDays(project.id).toSet()
            val nodes = buildPlanNodes(
                nodeDao.getNodesSync(project.id),
                database.projectDependencyDao().getForProject(project.id)
            )
            Pair(project, planProject(project.toPlanConfig(excluded), nodes, today))
        }
    }

    // Titulos de los nodos de un proyecto, para etiquetas
    suspend fun getNodeTitles(projectId: Long): Map<Long, String> =
        nodeDao.getNodesSync(projectId).associate { it.id to it.title }

    // ---------- Dependencias (fase 2a) ----------

    fun observeDependencies(projectId: Long): Flow<List<ProjectDependencyEntity>> =
        database.projectDependencyDao().observeForProject(projectId)

    fun observeAllDependencies(): Flow<List<ProjectDependencyEntity>> =
        database.projectDependencyDao().observeAll()

    // enabled = true: "successorId espera a predecessorId". enabled = false la quita.
    // Devuelve CYCLE si la relacion crea un ciclo, o si una tarea y su ancestro/descendiente se relacionan.
    suspend fun setDependency(projectId: Long, predecessorId: Long, successorId: Long, enabled: Boolean): OpResult {
        val r = database.withTransaction<OpResult> {
            val dao = database.projectDependencyDao()
            if (!enabled) {
                dao.delete(predecessorId, successorId)
                return@withTransaction OpResult.OK
            }
            if (predecessorId == successorId) return@withTransaction OpResult.CYCLE
            val nodes = nodeDao.getNodesSync(projectId)
            if (nodes.none { it.id == predecessorId } || nodes.none { it.id == successorId }) {
                return@withTransaction OpResult.NOT_FOUND
            }
            if (subtreeIds(nodes, predecessorId).contains(successorId) ||
                subtreeIds(nodes, successorId).contains(predecessorId)
            ) {
                return@withTransaction OpResult.CYCLE
            }
            dao.insert(ProjectDependencyEntity(predecessorId, successorId))
            val deps = dao.getForProject(projectId)
            if (orderWork(buildPlanNodes(nodes, deps)) == null) {
                dao.delete(predecessorId, successorId)
                return@withTransaction OpResult.CYCLE
            }
            OpResult.OK
        }
        return syncIfOk(r, projectId)
    }

    // ---------- Edicion directa en el Gantt (fase 2c) ----------

    // Crea una restriccion "no antes de" al mover una barra. Pone la fecha al dia indicado.
    suspend fun setNotBefore(nodeId: Long, day: Long?): OpResult {
        var pid = 0L
        val r = database.withTransaction<OpResult> {
            val node = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
            pid = node.projectId
            val nodes = nodeDao.getNodesSync(node.projectId)
            if (hasChildren(nodes, nodeId)) return@withTransaction OpResult.IS_GROUP
            nodeDao.updateNode(node.copy(notBeforeDate = day))
            OpResult.OK
        }
        return syncIfOk(r, pid)
    }

    // Cambia la duracion de una hoja (en minutos). Usa resizeLeaf para respetar el progreso.
    suspend fun setLeafMinutes(nodeId: Long, minutes: Int, nowMs: Long): OpResult {
        var pid = 0L
        val r = database.withTransaction<OpResult> {
            val node = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
            pid = node.projectId
            val nodes = nodeDao.getNodesSync(node.projectId)
            if (hasChildren(nodes, nodeId)) return@withTransaction OpResult.IS_GROUP
            if (node.isMilestone) return@withTransaction OpResult.INVALID_MINUTES
            if (minutes < MIN_LEAF_MINUTES) return@withTransaction OpResult.INVALID_MINUTES
            nodeDao.updateNode(resizeLeaf(node, minutes, nowMs))
            OpResult.OK
        }
        return syncIfOk(r, pid)
    }

    // Anade una tarea al final de la raiz (fase 2c, creacion desde cero en el Gantt)
    suspend fun addRootLeaf(projectId: Long, title: String, nowMs: Long, today: Long): OpResult {
        val r = database.withTransaction<OpResult> {
            val draft = NodeDraft(title, null, DEFAULT_LEAF_MINUTES, false, 0, null, null)
            val check = validateNodeDraft(draft, true)
            if (check != OpResult.OK) return@withTransaction check
            val nodes = nodeDao.getNodesSync(projectId)
            val nextOrder = (childrenOfNode(nodes, null).maxOfOrNull { it.sortOrder } ?: -1) + 1
            val notBefore = if (nodes.isEmpty()) today else null
            nodeDao.insertNode(
                ProjectNodeEntity(
                    projectId = projectId,
                    parentId = null,
                    title = title.trim(),
                    sortOrder = nextOrder,
                    estimatedMinutes = DEFAULT_LEAF_MINUTES,
                    notBeforeDate = notBefore,
                    createdAt = nowMs
                )
            )
            OpResult.OK
        }
        return syncIfOk(r, projectId)
    }

    // Guarda el plan del dia en el log: minutos planificados por hoja (sin borrar lo ya hecho).
    suspend fun snapshotPlanForDay(day: Long) {
        val log = database.projectDailyLogDao()
        log.deleteUnstartedPlannedForDay(day)
        for ((project, plan) in getActiveProjectPlansSync(day)) {
            val partsByNode = todayParts(plan, day).groupBy { it.nodeId }
            for ((nodeId, parts) in partsByNode) {
                val planned = parts.sumOf { it.minutes }
                val existing = log.getForNodeAndDay(nodeId, day)
                log.upsert(
                    ProjectDailyLogEntity(
                        nodeId = nodeId,
                        epochDay = day,
                        plannedMinutes = planned,
                        doneMinutes = existing?.doneMinutes ?: 0
                    )
                )
            }
        }
    }

    // Totales de proyectos de un dia cerrado (ya no lo usa DayCloser)
    suspend fun totalsForClosedDay(day: Long): DayProjectTotals {
        val logs = database.projectDailyLogDao().getForDay(day)
        return DayProjectTotals(
            planned = logs.count { it.plannedMinutes > 0 },
            done = logs.count { it.plannedMinutes > 0 && it.doneMinutes >= it.plannedMinutes }
        )
    }

    // Filas CSV de un proyecto, con dependencias y ruta critica
    suspend fun exportRows(projectId: Long, today: Long): List<List<String?>> {
        val project = projectDao.getProjectById(projectId) ?: return emptyList()
        val excluded = projectDao.getExcludedDays(projectId).toSet()
        val entities = nodeDao.getNodesSync(projectId)
        val deps = database.projectDependencyDao().getForProject(projectId)
        val plan = planFor(project, entities, deps, excluded, today)
        val titles = entities.associate { it.id to it.title }
        val depsByNode = deps.groupBy({ it.successorId }, { it.predecessorId })
        val rows = projectCsvRows(entities, plan, titles).toMutableList()
        val order = preorderNodeIds(entities.map { it.toPlanNode() })
        val fixed = rows.mapIndexed { index, row ->
            val id = order[index]
            val predTitles = depsByNode[id].orEmpty().mapNotNull { titles[it] }.joinToString(";")
            row.toMutableList().also { it[9] = predTitles }
        }
        return fixed
    }

    // Proyectos completados: par (true, terminado antes o en el deadline).
    // El dia de fin es el de la ultima hoja completada (hora de inicio de dia 0).
    suspend fun completedProjectsSummary(today: Long): List<Pair<Boolean, Boolean>> {
        val zone = ZoneId.systemDefault()
        val all = projectDao.getAllProjectsSync()
        return all.filter { it.status == ProjectStatus.COMPLETED }.map { project ->
            val last = nodeDao.getNodesSync(project.id).mapNotNull { it.completedAt }.maxOrNull()
            val end: Long? = last?.let { logicalDayOfMillis(it, 0, zone) }
            val deadline = project.deadlineDate
            val beforeDeadline = deadline != null && end != null && end <= deadline
            Pair(true, beforeDeadline)
        }
    }

    // Nodos de un proyecto para calcular la desviacion (fase 2e)
    suspend fun getNodesForDeviation(projectId: Long): List<ProjectNodeEntity> = nodeDao.getNodesSync(projectId)

    suspend fun getActiveProjectsOnce(): List<ProjectEntity> = projectDao.getActiveProjectsSync()

    // El enfoque ya se registra en focus_logs. Ya no suma progreso al objetivo: un objetivo solo
    // avanza al completar sus tareas, para que el reparto no cambie por tiempo de enfoque.
    suspend fun recordFocusMinutes(nodeId: Long, day: Long, minutes: Int, nowMs: Long) {
    }

    // ---------- Proyectos avanzados (fase 9) ----------

    // Asignaciones manuales de dias de un proyecto
    suspend fun getAssignments(projectId: Long): List<ProjectCellAssignmentEntity> =
        database.projectCellDao().getForProject(projectId)

    // Pares (nodo, dia) cuya tarea materializada de celda esta completada
    suspend fun getCompletedCellTasks(projectId: Long): Set<Pair<Long, Long>> =
        database.taskDao().getProjectTasksSync(projectId)
            .filter { it.isCompleted && it.deletedAt == null }
            .mapNotNull { t ->
                val key = t.projectKey
                val node = t.projectNodeId
                val day = t.date
                if (key != null && node != null && day != null && isProjectCellKey(key)) Pair(node, day) else null
            }
            .toSet()

    fun observeProjectTasks(projectId: Long): Flow<List<TaskEntity>> =
        database.taskDao().observeProjectTasks(projectId)

    fun observeAssignments(projectId: Long): Flow<List<ProjectCellAssignmentEntity>> =
        database.projectCellDao().observeForProject(projectId)

    // Cambia el tipo del proyecto. null equivale a SIMPLE.
    suspend fun setKind(projectId: Long, kind: String?): OpResult {
        if (!isValidProjectKind(kind)) return OpResult.INVALID_STATUS
        val project = projectDao.getProjectById(projectId) ?: return OpResult.NOT_FOUND
        projectDao.updateProject(project.copy(kind = kind))
        taskSync.syncProject(projectId)
        return OpResult.OK
    }

    // Cambia el modo de seguimiento y re-sincroniza las tareas materializadas.
    suspend fun setTrackingMode(projectId: Long, mode: String): OpResult {
        if (!isValidTrackingMode(mode)) return OpResult.INVALID_STATUS
        val project = projectDao.getProjectById(projectId) ?: return OpResult.NOT_FOUND
        projectDao.updateProject(project.copy(trackingMode = mode))
        taskSync.syncProject(projectId)
        return OpResult.OK
    }

    // Reemplaza todas las asignaciones de un proyecto avanzado.
    // cells = pares (nodeId, epochDay). Solo se aceptan hojas (no grupos ni hitos).
    suspend fun replaceAssignments(projectId: Long, cells: Set<Pair<Long, Long>>): OpResult {
        val r = database.withTransaction<OpResult> {
            val project = projectDao.getProjectById(projectId) ?: return@withTransaction OpResult.NOT_FOUND
            if (project.kind != ProjectKind.ADVANCED) return@withTransaction OpResult.INVALID_STATUS
            val nodes = nodeDao.getNodesSync(projectId)
            val leaves = nodes
                .filter { !it.isMilestone && !hasChildren(nodes, it.id) }
                .map { it.id }
                .toSet()
            if (cells.any { it.first !in leaves }) return@withTransaction OpResult.INVALID_STATUS
            val dao = database.projectCellDao()
            dao.deleteForProject(projectId)
            dao.insertAll(cells.map { ProjectCellAssignmentEntity(it.first, it.second) })
            OpResult.OK
        }
        return syncIfOk(r, projectId)
    }
}
