package com.example.taskflow.data.repositories

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.taskflow.R
import com.example.taskflow.data.daos.ProjectCellDao
import com.example.taskflow.data.daos.ProjectDao
import com.example.taskflow.data.daos.ProjectDependencyDao
import com.example.taskflow.data.daos.ProjectNodeDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.db.AppDatabase
import com.example.taskflow.domain.ProjectStatus
import com.example.taskflow.domain.TrackingMode
import com.example.taskflow.domain.desiredProjectTasks
import com.example.taskflow.domain.effectiveTrackingMode
import com.example.taskflow.domain.frozenMinutesByNode
import com.example.taskflow.domain.priorPartsByNode
import com.example.taskflow.domain.leafNodeIds
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.reconcileProjectTasks
import com.example.taskflow.domain.retainedMinutesByNode
import com.example.taskflow.domain.toTaskEntity
import com.example.taskflow.scheduling.SchedulingCoordinator
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// Mantiene en la tabla tasks las tareas que cada proyecto desea tener materializadas.
// No depende de ProjectRepository (el repositorio depende de esta clase).
// SchedulingCoordinator se inyecta con Lazy para romper el ciclo con ProjectNotifier.
@Singleton
class ProjectTaskSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val projectDao: ProjectDao,
    private val nodeDao: ProjectNodeDao,
    private val dependencyDao: ProjectDependencyDao,
    private val cellDao: ProjectCellDao,
    private val taskDao: TaskDao,
    private val settingsRepo: SettingsRepository,
    private val coordinator: Lazy<SchedulingCoordinator>
) {
    private val mutex = Mutex()

    private companion object {
        const val TAG = "ProjectTaskSync"
    }

    // Sincroniza un proyecto. reopenedNodeId: nodo que se acaba de reabrir; sus tareas completadas
    // de hoy en adelante (o sin fecha) se borran para liberar sus claves.
    suspend fun syncProject(projectId: Long, reopenedNodeId: Long? = null, notify: Boolean = true) {
        val changed = mutex.withLock {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            val closed = settingsRepo.getLastClosedDaySync()
            safeSync(projectId, today, closed, reopenedNodeId)
        }
        if (changed && notify) coordinator.get().onDataChanged()
    }

    // Sincroniza todos los proyectos y borra las tareas pendientes huerfanas.
    suspend fun syncAll(notify: Boolean = true) {
        val changed = mutex.withLock {
            val today = logicalDateNow(settingsRepo.getDayStartHourSync())
            val closed = settingsRepo.getLastClosedDaySync()
            var any = false
            val ids = projectDao.getAllProjectsSync().map { it.id }
            for (id in ids) {
                if (safeSync(id, today, closed, null)) any = true
            }
            try {
                val idSet = ids.toSet()
                val orphans = taskDao.getAllProjectTasksSync()
                    .filter { !it.isCompleted && it.projectId != null && it.projectId !in idSet }
                    .map { it.id }
                if (orphans.isNotEmpty()) {
                    orphans.chunked(500).forEach { taskDao.deleteTasksByIds(it) }
                    any = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "orphan cleanup failed", e)
            }
            any
        }
        if (changed && notify) coordinator.get().onDataChanged()
    }

    // Borra todas las tareas de un proyecto (borrado definitivo del proyecto)
    suspend fun purgeProject(projectId: Long) {
        mutex.withLock { taskDao.deleteProjectTasks(projectId) }
        coordinator.get().onDataChanged()
    }

    private suspend fun safeSync(projectId: Long, today: Long, closedUpTo: Long, reopenedNodeId: Long?): Boolean {
        return try {
            syncOne(projectId, today, closedUpTo, reopenedNodeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "sync failed for project " + projectId, e)
            false
        }
    }

    private suspend fun syncOne(projectId: Long, today: Long, closedUpTo: Long, reopenedNodeId: Long?): Boolean {
        val nowMs = System.currentTimeMillis()
        var existing = taskDao.getProjectTasksSync(projectId)
        val project = projectDao.getProjectById(projectId)
        if (project == null) {
            val ids = existing.filter { !it.isCompleted }.map { it.id }
            if (ids.isEmpty()) return false
            ids.chunked(500).forEach { taskDao.deleteTasksByIds(it) }
            return true
        }

        var changed = false
        if (reopenedNodeId != null) {
            val stale = existing
                .filter { it.projectNodeId == reopenedNodeId && it.isCompleted && (it.date == null || (it.date ?: 0L) >= today) }
                .map { it.id }
            if (stale.isNotEmpty()) {
                stale.chunked(500).forEach { taskDao.deleteTasksByIds(it) }
                val staleSet = stale.toSet()
                existing = existing.filter { it.id !in staleSet }
                changed = true
            }
        }

        val nodes = nodeDao.getNodesSync(projectId)
        val dependencies = dependencyDao.getForProject(projectId)
        val excluded = projectDao.getExcludedDays(projectId).toSet()
        val assignments = cellDao.getForProject(projectId)
        val mode = effectiveTrackingMode(project.trackingMode)
        val active = project.deletedAt == null && !project.isTemplate && project.status == ProjectStatus.ACTIVE
        val prefix = context.getString(R.string.pt_daily_prefix)

        val desired = desiredProjectTasks(
            project = project,
            nodes = nodes,
            dependencies = dependencies,
            excludedDays = excluded,
            assignments = assignments,
            retainedMinutes = retainedMinutesByNode(existing, today),
            today = today,
            dailyPrefix = prefix,
            frozenMinutes = frozenMinutesByNode(existing, today),
            priorParts = priorPartsByNode(existing, today)
        )
        val leaves = leafNodeIds(nodes)
        val open = nodes.filter { it.id in leaves && !it.completed }.map { it.id }.toSet()
        val done = nodes.filter { it.id in leaves && it.completed }.map { it.id }.toSet()

        val result = reconcileProjectTasks(
            desired = desired,
            existing = existing,
            today = today,
            closedUpTo = if (active) closedUpTo else Long.MAX_VALUE,
            openNodeIds = open,
            completedNodeIds = done,
            keepPastPending = active && mode != TrackingMode.DAILY,
            nowMs = nowMs
        )
        if (result.toDelete.isEmpty() && result.toUpdate.isEmpty() && result.toInsert.isEmpty()) return changed

        database.withTransaction {
            result.toDelete.chunked(500).forEach { taskDao.deleteTasksByIds(it) }
            result.toUpdate.forEach { taskDao.updateTask(it) }
            result.toInsert.forEach { taskDao.insertTask(it.toTaskEntity(nowMs)) }
        }
        return true
    }
}
