package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectCellAssignmentEntity
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.data.entities.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectTasksTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()

    private fun project(
        kind: String? = null,
        mode: String = TrackingMode.DYNAMIC,
        deadline: Long? = null,
        status: String = ProjectStatus.ACTIVE
    ) = ProjectEntity(
        id = 1L,
        name = "Web",
        colorArgb = 0,
        startDate = mon,
        deadlineDate = deadline,
        dailyCapacityMinutes = 240,
        workingDaysMask = 31,
        status = status,
        createdAt = 0L,
        kind = kind,
        trackingMode = mode
    )

    private fun node(
        id: Long,
        parent: Long? = null,
        minutes: Int = 60,
        done: Int = 0,
        completed: Boolean = false,
        milestone: Boolean = false
    ) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = parent,
        title = "T" + id,
        sortOrder = id.toInt(),
        isMilestone = milestone,
        estimatedMinutes = minutes,
        doneMinutes = done,
        completed = completed,
        createdAt = 0L
    )

    private fun cell(nodeId: Long, day: Long) = ProjectCellAssignmentEntity(nodeId, day)

    private fun desired(
        p: ProjectEntity,
        nodes: List<ProjectNodeEntity>,
        assignments: List<ProjectCellAssignmentEntity> = emptyList(),
        retained: Map<Long, Int> = emptyMap(),
        excluded: Set<Long> = emptySet(),
        today: Long = mon
    ) = desiredProjectTasks(p, nodes, emptyList(), excluded, assignments, retained, today, "Avanzar en")

    // ---------- modo ----------

    @Test
    fun `tracking mode validation and default`() {
        assertTrue(isValidTrackingMode("DAILY"))
        assertFalse(isValidTrackingMode("OTHER"))
        assertFalse(isValidTrackingMode(null))
        assertEquals(TrackingMode.DYNAMIC, effectiveTrackingMode(null))
        assertEquals(TrackingMode.DYNAMIC, effectiveTrackingMode("OTHER"))
        assertEquals(TrackingMode.NONE, effectiveTrackingMode("NONE"))
    }

    // ---------- SIMPLE / DYNAMIC ----------

    @Test
    fun `simple dynamic gives one task per plan part`() {
        val result = desired(project(), listOf(node(1, minutes = 360)))
        assertEquals(2, result.size)
        assertEquals("P1:N1:D" + mon, result[0].key)
        assertEquals("Web: T1 (parte 1/2)", result[0].title)
        assertEquals(mon, result[0].date)
        assertEquals(240, result[0].estimatedMinutes)
        assertEquals("P1:N1:D" + (mon + 1), result[1].key)
        assertEquals(120, result[1].estimatedMinutes)
    }

    @Test
    fun `retained minutes are discounted from the plan`() {
        assertTrue(desired(project(), listOf(node(1, minutes = 240)), retained = mapOf(1L to 240)).isEmpty())
        val partial = desired(project(), listOf(node(1, minutes = 240)), retained = mapOf(1L to 120))
        assertEquals(1, partial.size)
        assertEquals(120, partial[0].estimatedMinutes)
    }

    @Test
    fun `inactive project and completed node give nothing`() {
        assertTrue(desired(project(status = ProjectStatus.PAUSED), listOf(node(1))).isEmpty())
        assertTrue(desired(project(), listOf(node(1, completed = true))).isEmpty())
    }

    // ---------- SIMPLE / DAILY ----------

    @Test
    fun `simple daily covers thirty days skipping weekends`() {
        val result = desired(project(mode = TrackingMode.DAILY), listOf(node(1)))
        assertEquals(22, result.size)
        assertEquals(mon, result.first().date)
        assertEquals("Avanzar en Web", result.first().title)
        assertNull(result.first().nodeId)
        assertTrue(result.all { (dayBitOf(it.date!!) and 31) != 0 })
    }

    @Test
    fun `simple daily stops at deadline and skips excluded days`() {
        val p = project(mode = TrackingMode.DAILY, deadline = mon + 3)
        assertEquals(4, desired(p, listOf(node(1))).size)
        assertEquals(3, desired(p, listOf(node(1)), excluded = setOf(mon + 1)).size)
    }

    @Test
    fun `simple daily stops when every leaf is done but works without nodes`() {
        val p = project(mode = TrackingMode.DAILY)
        assertTrue(desired(p, listOf(node(1, completed = true))).isEmpty())
        assertEquals(22, desired(p, emptyList()).size)
    }

    // ---------- NONE ----------

    @Test
    fun `none mode gives one undated task per pending leaf`() {
        val nodes = listOf(
            node(10, minutes = 0),
            node(11, parent = 10),
            node(12, parent = 10, completed = true),
            node(20)
        )
        val result = desired(project(mode = TrackingMode.NONE), nodes)
        assertEquals(listOf("P1:N11:L", "P1:N20:L"), result.map { it.key })
        assertTrue(result.all { it.date == null && !it.managedDate })
        assertEquals("Web: T11", result[0].title)
        assertEquals(60, result[0].estimatedMinutes)
    }

    // ---------- ADVANCED ----------

    @Test
    fun `advanced dynamic gives one task per future cell of a pending leaf`() {
        val nodes = listOf(node(1), node(2, completed = true), node(10, minutes = 0), node(11, parent = 10))
        val cells = listOf(
            cell(1, mon), cell(1, mon + 1), cell(2, mon + 1), cell(1, mon - 1), cell(10, mon)
        )
        val result = desired(project(kind = ProjectKind.ADVANCED), nodes, cells)
        assertEquals(listOf("P1:N1:C" + mon, "P1:N1:C" + (mon + 1)), result.map { it.key })
        assertEquals("Web: T1", result[0].title)
    }

    @Test
    fun `advanced daily gives one reminder per assigned day`() {
        val nodes = listOf(node(1), node(2, completed = true))
        val cells = listOf(cell(1, mon + 1), cell(2, mon + 1), cell(2, mon), cell(2, mon + 2))
        val result = desired(project(kind = ProjectKind.ADVANCED, mode = TrackingMode.DAILY), nodes, cells)
        assertEquals(listOf(mon, mon + 1), result.map { it.date })
        assertEquals(listOf(true, false), result.map { it.completed })
        assertEquals("P1:DAY" + mon, result[0].key)
    }

    // ---------- reconcile ----------

    private fun task(
        id: Long,
        key: String,
        date: Long?,
        node: Long? = 1L,
        completed: Boolean = false,
        title: String = "T",
        deleted: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        date = date,
        isCompleted = completed,
        createdAt = 0L,
        deletedAt = deleted,
        projectId = 1L,
        projectNodeId = node,
        projectKey = key
    )

    private fun want(key: String, date: Long?, title: String = "T", managed: Boolean = true, completed: Boolean = false) =
        DesiredTask(key, 1L, 1L, title, date, null, 0, managed, completed)

    private fun reconcile(
        desired: List<DesiredTask>,
        existing: List<TaskEntity>,
        closedUpTo: Long = mon - 1,
        open: Set<Long> = setOf(1L),
        completedNodes: Set<Long> = emptySet(),
        keepPast: Boolean = true
    ) = reconcileProjectTasks(desired, existing, mon, closedUpTo, open, completedNodes, keepPast, 999L)

    @Test
    fun `reconcile inserts missing keys`() {
        val r = reconcile(listOf(want("K", mon)), emptyList())
        assertEquals(1, r.toInsert.size)
        assertTrue(r.toUpdate.isEmpty() && r.toDelete.isEmpty())
    }

    @Test
    fun `reconcile updates pending tasks in place`() {
        val r = reconcile(listOf(want("K", mon + 2, "New")), listOf(task(1, "K", mon + 1, title = "Old")))
        assertTrue(r.toInsert.isEmpty() && r.toDelete.isEmpty())
        assertEquals(1, r.toUpdate.size)
        assertEquals("New", r.toUpdate[0].title)
        assertEquals(mon + 2, r.toUpdate[0].date)
        assertEquals(1L, r.toUpdate[0].id)
    }

    @Test
    fun `completed task is untouched and blocks reinsertion`() {
        val r = reconcile(listOf(want("K", mon + 1)), listOf(task(1, "K", mon, completed = true)))
        assertTrue(r.toInsert.isEmpty() && r.toUpdate.isEmpty() && r.toDelete.isEmpty())
    }

    @Test
    fun `future pending task that is no longer desired is deleted`() {
        val r = reconcile(emptyList(), listOf(task(7, "K", mon + 3)))
        assertEquals(listOf(7L), r.toDelete)
    }

    @Test
    fun `past pending task is retained while its node is open`() {
        val r = reconcile(emptyList(), listOf(task(1, "K", mon - 1)))
        assertTrue(r.toInsert.isEmpty() && r.toUpdate.isEmpty() && r.toDelete.isEmpty())
    }

    @Test
    fun `past pending task without retention is deleted only after its day is closed`() {
        val t = task(1, "K", mon - 1, node = null)
        assertEquals(listOf(1L), reconcile(emptyList(), listOf(t), closedUpTo = mon - 1, keepPast = false).toDelete)
        assertTrue(reconcile(emptyList(), listOf(t), closedUpTo = mon - 2, keepPast = false).toDelete.isEmpty())
    }

    @Test
    fun `tasks of a completed node are completed or deleted`() {
        val existing = listOf(
            task(1, "A", mon, node = 5L),
            task(2, "B", mon + 2, node = 5L),
            task(3, "C", null, node = 5L)
        )
        val r = reconcile(emptyList(), existing, completedNodes = setOf(5L))
        assertEquals(listOf(1L, 3L), r.toUpdate.map { it.id })
        assertTrue(r.toUpdate.all { it.isCompleted && it.completedAt == 999L })
        assertEquals(listOf(2L), r.toDelete)
    }

    @Test
    fun `tasks of a node that no longer exists are deleted`() {
        val r = reconcile(emptyList(), listOf(task(1, "K", mon - 1, node = 9L)))
        assertEquals(listOf(1L), r.toDelete)
    }

    @Test
    fun `unmanaged date is never overwritten`() {
        val r = reconcile(listOf(want("K", null, managed = false)), listOf(task(1, "K", mon + 5)))
        assertTrue(r.toUpdate.isEmpty() && r.toInsert.isEmpty() && r.toDelete.isEmpty())
    }

    @Test
    fun `soft deleted row is removed and the key is inserted again`() {
        val r = reconcile(listOf(want("K", mon + 1)), listOf(task(1, "K", mon + 1, deleted = 5L)))
        assertEquals(listOf(1L), r.toDelete)
        assertEquals(1, r.toInsert.size)
    }

    @Test
    fun `desired completed marks the pending task as completed`() {
        val r = reconcile(listOf(want("K", mon, completed = true)), listOf(task(1, "K", mon, node = null)))
        assertEquals(1, r.toUpdate.size)
        assertTrue(r.toUpdate[0].isCompleted)
    }

    @Test
    fun `retained minutes sum only past pending tasks`() {
        val list = listOf(
            task(1, "A", mon - 1).copy(estimatedMinutes = 60),
            task(2, "B", mon - 2).copy(estimatedMinutes = 30),
            task(3, "C", mon - 1, completed = true).copy(estimatedMinutes = 100),
            task(4, "D", mon + 1).copy(estimatedMinutes = 50),
            task(5, "E", mon - 1, node = 2L).copy(estimatedMinutes = 20)
        )
        assertEquals(mapOf(1L to 90, 2L to 20), retainedMinutesByNode(list, mon))
    }

    @Test
    fun `desired task converts to an entity`() {
        val e = want("K", mon, "X", completed = true).toTaskEntity(50L)
        assertEquals("K", e.projectKey)
        assertEquals(1L, e.projectId)
        assertTrue(e.isCompleted)
        assertEquals(50L, e.completedAt)
        assertEquals(50L, e.createdAt)
    }

    // ---------- avanzados ----------

    @Test
    fun `advanced progress counts completed leaves`() {
        val nodes = listOf(
            node(1, completed = true),
            node(2),
            node(10, minutes = 0),
            node(11, parent = 10, completed = true),
            node(30, milestone = true)
        )
        assertEquals(67, advancedProgressPercent(nodes))
        assertEquals(0, advancedProgressPercent(emptyList()))
        assertEquals(setOf(1L, 2L, 11L), leafNodeIds(nodes))
    }

    @Test
    fun `logical day of millis respects the day start hour`() {
        val zone = java.time.ZoneOffset.UTC
        val ms = LocalDate.of(2026, 11, 24).atTime(2, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 23).toEpochDay(), logicalDayOfMillis(ms, 4, zone))
        assertEquals(LocalDate.of(2026, 11, 24).toEpochDay(), logicalDayOfMillis(ms, 0, zone))
    }
}
