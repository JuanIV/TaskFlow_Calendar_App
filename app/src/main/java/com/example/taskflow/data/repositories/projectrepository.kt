package com.example.taskflow.data.repositories

import androidx.room.withTransaction
import com.example.taskflow.data.daos.ProjectDao
import com.example.taskflow.data.daos.ProjectNodeDao
import com.example.taskflow.data.db.AppDatabase
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectExcludedDayEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.data.entities.ProjectDependencyEntity
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
import com.example.taskflow.domain.cleanNotes
import com.example.taskflow.domain.groupToLeaf
import com.example.taskflow.domain.hasChildren
import com.example.taskflow.domain.insertAt
import com.example.taskflow.domain.isZeroLength
import com.example.taskflow.domain.leafToGroup
import com.example.taskflow.domain.orderWork
import com.example.taskflow.domain.planProject
import com.example.taskflow.domain.preorderNodeIds
import com.example.taskflow.domain.resizeLeaf
import com.example.taskflow.domain.subtreeHasProgress
import com.example.taskflow.domain.subtreeIds
import com.example.taskflow.domain.toMilestone
import com.example.taskflow.domain.validateNodeDraft
import com.example.taskflow.domain.validateProject
import kotlinx.coroutines.flow.Flow
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
    private val nodeDao: ProjectNodeDao
) {
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
                        createdAt = nowMs
                    )
                )
                if (excluded.isNotEmpty()) {
                    projectDao.insertExcludedDays(excluded.map { ProjectExcludedDayEntity(id, it) })
                }
            } else {
                projectDao.updateProject(
                    existing.copy(
                        name = name,
                        description = description,
                        colorArgb = form.colorArgb,
                        icon = form.icon,
                        startDate = form.startDate,
                        deadlineDate = form.deadlineDate,
                        dailyCapacityMinutes = form.capacityMinutes,
                        workingDaysMask = form.workingDaysMask
                    )
                )
                projectDao.deleteExcludedDays(existing.id)
                if (excluded.isNotEmpty()) {
                    projectDao.insertExcludedDays(excluded.map { ProjectExcludedDayEntity(existing.id, it) })
                }
            }
        }
        return OpResult.OK
    }

    suspend fun softDeleteProject(projectId: Long, nowMs: Long) = projectDao.softDeleteProject(projectId, nowMs)

    suspend fun restoreProject(projectId: Long) = projectDao.restoreProject(projectId)

    suspend fun permanentlyDeleteProject(projectId: Long) = projectDao.permanentlyDeleteProject(projectId)

    // Cambia el estado respetando las transiciones permitidas
    suspend fun setStatus(projectId: Long, newStatus: String): OpResult {
        val project = projectDao.getProjectById(projectId) ?: return OpResult.NOT_FOUND
        if (!canTransitionProjectStatus(project.status, newStatus)) return OpResult.INVALID_STATUS
        projectDao.updateProject(project.copy(status = newStatus))
        return OpResult.OK
    }

    // Copia un proyecto (RF-7). Reinicia progreso y adelantos. Devuelve el id nuevo o null.
    suspend fun duplicateProject(
        sourceId: Long,
        newName: String,
        asTemplate: Boolean,
        newStartDate: Long,
        nowMs: Long
    ): Long? {
        val name = newName.trim()
        if (name.isEmpty()) return null
        return database.withTransaction<Long?> {
            val source = projectDao.getProjectById(sourceId) ?: return@withTransaction null
            val excluded = projectDao.getExcludedDays(sourceId)
            val newId = projectDao.insertProject(
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
                projectDao.insertExcludedDays(excluded.map { ProjectExcludedDayEntity(newId, it) })
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
                        projectId = newId,
                        parentId = old.parentId?.let { newIds[it] },
                        doneMinutes = 0,
                        completed = false,
                        completedAt = null,
                        actualMinutes = null,
                        pulledForwardDay = null,
                        createdAt = nowMs
                    )
                )
                val depDao = database.projectDependencyDao()
            for (dep in depDao.getForProject(sourceId)) {
                val p = newIds[dep.predecessorId]
                val s = newIds[dep.successorId]
                if (p != null && s != null) depDao.insert(ProjectDependencyEntity(p, s))
            }
            newIds[oldId] = newNodeId
            }
            val depDao = database.projectDependencyDao()
            for (dep in depDao.getForProject(sourceId)) {
                val p = newIds[dep.predecessorId]
                val s = newIds[dep.successorId]
                if (p != null && s != null) depDao.insert(ProjectDependencyEntity(p, s))
            }
            newId
        }
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
    suspend fun addNode(projectId: Long, parentId: Long?, draft: NodeDraft, nowMs: Long): OpResult =
        database.withTransaction<OpResult> {
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
            }
            val nextOrder = (childrenOfNode(nodes, parentId).maxOfOrNull { it.sortOrder } ?: -1) + 1
            nodeDao.insertNode(newNodeEntity(projectId, parentId, draft, nextOrder, nowMs))
            OpResult.OK
        }

    // Edita un nodo. En grupos se ignoran la duracion y la marca de hito.
    suspend fun updateNode(nodeId: Long, draft: NodeDraft, nowMs: Long): OpResult =
        database.withTransaction<OpResult> {
            val current = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
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

    // Borra un nodo y sus descendientes. Si el padre se queda sin hijos, vuelve a ser hoja.
    suspend fun deleteNode(nodeId: Long, confirmed: Boolean): OpResult =
        database.withTransaction<OpResult> {
            val target = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
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

    // Mueve un nodo a otro padre (null = raiz) en la posicion newIndex (se recorta al rango valido)
    suspend fun moveNode(nodeId: Long, newParentId: Long?, newIndex: Int): OpResult =
        database.withTransaction<OpResult> {
            val target = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
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

    // Completa o reabre una hoja o un hito. Sobre un grupo devuelve IS_GROUP.
    suspend fun setNodeCompleted(nodeId: Long, completed: Boolean, actualMinutes: Int?, nowMs: Long): OpResult =
        database.withTransaction<OpResult> {
            val node = nodeDao.getNodeById(nodeId) ?: return@withTransaction OpResult.NOT_FOUND
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

    // ---------- Operaciones de 1a (sin cambios de comportamiento) ----------

    // Registra minutos trabajados en una hoja. Si queda completa, marca completed.
    suspend fun markPartDone(nodeId: Long, minutes: Int, nowMs: Long) {
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

    // Completa la parte de hoy de una hoja. Si es la ultima parte pendiente, completa la hoja entera.
    suspend fun completeTodayPart(nodeId: Long, minutes: Int, nowMs: Long) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        if (isZeroLength(entity.toPlanNode())) return
        val remaining = maxOf(0, entity.estimatedMinutes - entity.doneMinutes)
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
    }

    // Adelantar a hoy (RF-19)
    suspend fun setPulledForward(nodeId: Long, today: Long) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        nodeDao.updateNode(entity.copy(pulledForwardDay = today))
    }

    // Calcula el plan del proyecto. today debe venir de logicalDateNow(dayStartHour).
    suspend fun loadPlan(projectId: Long, today: Long): ProjectPlan? {
        val project = projectDao.getProjectById(projectId) ?: return null
        val excluded = projectDao.getExcludedDays(projectId).toSet()
        val nodes = buildPlanNodes(
            nodeDao.getNodesSync(projectId),
            database.projectDependencyDao().getForProject(projectId)
        )
        return planProject(project.toPlanConfig(excluded), nodes, today)
    }

    // Proyectos activos con su plan de hoy, para Tareas, widget y notificacion
    suspend fun getActiveProjectPlansSync(today: Long): List<Pair<ProjectEntity, ProjectPlan>> {
        val projects = projectDao.getActiveProjectsSync()
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
    suspend fun setDependency(projectId: Long, predecessorId: Long, successorId: Long, enabled: Boolean): OpResult =
        database.withTransaction<OpResult> {
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
}
