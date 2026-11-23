package com.example.taskflow.data.repositories

import com.example.taskflow.data.daos.ProjectDao
import com.example.taskflow.data.daos.ProjectNodeDao
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectExcludedDayEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.domain.PlanConfig
import com.example.taskflow.domain.PlanNode
import com.example.taskflow.domain.ProjectPlan
import com.example.taskflow.domain.ProjectStatus
import com.example.taskflow.domain.applyPartDone
import com.example.taskflow.domain.isZeroLength
import com.example.taskflow.domain.planProject
import com.example.taskflow.domain.preorderNodeIds
import javax.inject.Inject
import javax.inject.Singleton

fun ProjectNodeEntity.toPlanNode(): PlanNode = PlanNode(
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
    private val projectDao: ProjectDao,
    private val nodeDao: ProjectNodeDao
) {
    suspend fun createProject(project: ProjectEntity, excludedDays: List<Long>): Long {
        val id = projectDao.insertProject(project)
        if (excludedDays.isNotEmpty()) {
            projectDao.insertExcludedDays(excludedDays.distinct().map { ProjectExcludedDayEntity(id, it) })
        }
        return id
    }

    // Reemplaza los datos del proyecto y su lista de dias excluidos
    suspend fun updateProject(project: ProjectEntity, excludedDays: List<Long>) {
        projectDao.updateProject(project)
        projectDao.deleteExcludedDays(project.id)
        if (excludedDays.isNotEmpty()) {
            projectDao.insertExcludedDays(excludedDays.distinct().map { ProjectExcludedDayEntity(project.id, it) })
        }
    }

    suspend fun softDeleteProject(projectId: Long, nowMs: Long) = projectDao.softDeleteProject(projectId, nowMs)

    suspend fun restoreProject(projectId: Long) = projectDao.restoreProject(projectId)

    suspend fun permanentlyDeleteProject(projectId: Long) = projectDao.permanentlyDeleteProject(projectId)

    suspend fun getProject(projectId: Long): ProjectEntity? = projectDao.getProjectById(projectId)

    suspend fun getExcludedDays(projectId: Long): List<Long> = projectDao.getExcludedDays(projectId)

    suspend fun addNode(node: ProjectNodeEntity): Long = nodeDao.insertNode(node)

    suspend fun updateNode(node: ProjectNodeEntity) = nodeDao.updateNode(node)

    suspend fun deleteNode(nodeId: Long) = nodeDao.deleteNode(nodeId)

    suspend fun getNodes(projectId: Long): List<ProjectNodeEntity> = nodeDao.getNodesSync(projectId)

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

    // Completa una hoja. actualMinutes es el tiempo real opcional (RF-20).
    suspend fun completeNode(nodeId: Long, actualMinutes: Int?, nowMs: Long) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        nodeDao.updateNode(
            entity.copy(
                doneMinutes = entity.estimatedMinutes,
                completed = true,
                completedAt = nowMs,
                actualMinutes = actualMinutes
            )
        )
    }

    // Vuelve a pendiente desde cero
    suspend fun reopenNode(nodeId: Long) {
        val entity = nodeDao.getNodeById(nodeId) ?: return
        nodeDao.updateNode(entity.copy(doneMinutes = 0, completed = false, completedAt = null, actualMinutes = null))
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
        val nodes = nodeDao.getNodesSync(projectId).map { it.toPlanNode() }
        return planProject(project.toPlanConfig(excluded), nodes, today)
    }

    // Copia un proyecto (RF-7). Reinicia progreso y adelantos. Devuelve el id nuevo o null.
    suspend fun duplicateProject(
        sourceId: Long,
        newName: String,
        asTemplate: Boolean,
        newStartDate: Long,
        nowMs: Long
    ): Long? {
        val source = projectDao.getProjectById(sourceId) ?: return null
        val excluded = projectDao.getExcludedDays(sourceId)
        val newId = projectDao.insertProject(
            source.copy(
                id = 0,
                name = newName,
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
            val newParent = old.parentId?.let { newIds[it] }
            val newNodeId = nodeDao.insertNode(
                old.copy(
                    id = 0,
                    projectId = newId,
                    parentId = newParent,
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
        return newId
    }
}
