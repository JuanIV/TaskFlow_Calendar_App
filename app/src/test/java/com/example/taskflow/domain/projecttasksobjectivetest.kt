package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectCellAssignmentEntity
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.data.entities.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectTasksObjectiveTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()
    private val tue = mon + 1
    private val wed = mon + 2

    private fun project(kind: String? = null) = ProjectEntity(
        id = 1L,
        name = "Web",
        colorArgb = 0,
        startDate = mon,
        dailyCapacityMinutes = 240,
        workingDaysMask = 31,
        status = ProjectStatus.ACTIVE,
        createdAt = 0L,
        kind = kind
    )

    private fun node(id: Long, minutes: Int, done: Int = 0, completed: Boolean = false) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = null,
        title = "T" + id,
        sortOrder = id.toInt(),
        estimatedMinutes = minutes,
        doneMinutes = done,
        completed = completed,
        createdAt = 0L
    )

    private fun task(id: Long, key: String, date: Long, minutes: Int, completed: Boolean, node: Long = 1L) = TaskEntity(
        id = id,
        title = "x",
        date = date,
        isCompleted = completed,
        createdAt = 0L,
        estimatedMinutes = minutes,
        projectId = 1L,
        projectNodeId = node,
        projectKey = key
    )

    private fun plan(
        nodes: List<ProjectNodeEntity>,
        existing: List<TaskEntity>,
        today: Long,
        kind: String? = null,
        cells: List<ProjectCellAssignmentEntity> = emptyList()
    ) = desiredProjectTasks(
        project(kind), nodes, emptyList(), emptySet(), cells,
        retainedMinutesByNode(existing, today), today, "Avanzar en",
        frozenMinutesByNode(existing, today), priorPartsByNode(existing, today)
    )

    private fun reconcile(desired: List<DesiredTask>, existing: List<TaskEntity>, today: Long) =
        reconcileProjectTasks(desired, existing, today, today - 1, setOf(1L), emptySet(), true, 9L)

    @Test
    fun `completing todays part does not pull tomorrows part`() {
        val existing = listOf(task(1, "P1:N1:D" + mon, mon, 240, true))
        val result = plan(listOf(node(1, 360, done = 240)), existing, mon)
        assertEquals(listOf("P1:N1:D" + mon, "P1:N1:D" + tue), result.map { it.key })
        assertEquals(listOf(240, 120), result.map { it.estimatedMinutes })
        assertEquals(listOf("Web: T1 (1/2)", "Web: T1 (2/2)"), result.map { it.title })
        val r = reconcile(result, existing, mon)
        assertEquals(listOf("P1:N1:D" + tue), r.toInsert.map { it.key })
        assertTrue(r.toUpdate.isEmpty() && r.toDelete.isEmpty())
    }

    @Test
    fun `next day the remaining part keeps its number`() {
        val existing = listOf(task(1, "P1:N1:D" + mon, mon, 240, true))
        val result = plan(listOf(node(1, 360, done = 240)), existing, tue)
        assertEquals(1, result.size)
        assertEquals("P1:N1:D" + tue, result[0].key)
        assertEquals(120, result[0].estimatedMinutes)
        assertEquals("Web: T1 (2/2)", result[0].title)
    }

    @Test
    fun `advancing a future part and keeping the plan changes nothing`() {
        val existing = listOf(task(1, "P1:N1:D" + tue, tue, 240, true))
        val result = plan(listOf(node(1, 600, done = 240)), existing, mon)
        assertEquals(listOf(mon, tue, wed), result.map { it.date })
        val r = reconcile(result, existing, mon)
        assertEquals(listOf("P1:N1:D" + mon, "P1:N1:D" + wed), r.toInsert.map { it.key })
        assertTrue(r.toUpdate.isEmpty() && r.toDelete.isEmpty())
    }

    @Test
    fun `advancing with replanning fills the hole`() {
        val existing = listOf(task(1, "P1:N1:D" + tue + "~", mon, 240, true))
        val result = plan(listOf(node(1, 600, done = 240)), existing, mon)
        assertEquals(listOf("P1:N1:D" + mon, "P1:N1:D" + tue), result.map { it.key })
        assertEquals(listOf(240, 120), result.map { it.estimatedMinutes })
        assertEquals(listOf("Web: T1 (2/3)", "Web: T1 (3/3)"), result.map { it.title })
    }

    @Test
    fun `frozen and prior parts are counted as expected`() {
        val existing = listOf(
            task(1, "P1:N1:D" + mon, mon, 100, true),
            task(2, "P1:N1:D" + tue, tue, 50, true),
            task(3, "P1:N1:D" + tue + "~", mon, 30, true),
            task(4, "P1:N1:D" + (mon - 1), mon - 1, 20, true),
            task(5, "P1:N2:D" + tue, tue, 10, false)
        )
        assertEquals(mapOf(1L to 150), frozenMinutesByNode(existing, mon))
        assertEquals(mapOf(1L to 2), priorPartsByNode(existing, mon))
    }

    @Test
    fun `advanced tasks are numbered by working day of the objective`() {
        val cells = listOf(
            ProjectCellAssignmentEntity(1L, mon),
            ProjectCellAssignmentEntity(1L, tue),
            ProjectCellAssignmentEntity(1L, wed)
        )
        val result = plan(listOf(node(1, 0)), emptyList(), tue, ProjectKind.ADVANCED, cells)
        assertEquals(listOf("P1:N1:C" + tue, "P1:N1:C" + wed), result.map { it.key })
        assertEquals(listOf("Web: T1 (2/3)", "Web: T1 (3/3)"), result.map { it.title })
    }

    @Test
    fun `objective title omits the counter for a single day`() {
        assertEquals("W: T", projectObjectiveTitle("W", "T", 1, 1))
        assertEquals("W: T (1/2)", projectObjectiveTitle("W", "T", 1, 2))
    }
}
