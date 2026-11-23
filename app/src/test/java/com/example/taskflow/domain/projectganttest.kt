package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectGanttTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()
    private val cfg = PlanConfig(mon, null, 240, 31, emptySet(), false)

    private fun entity(id: Long, parent: Long? = null, minutes: Int = 60, order: Int = id.toInt()) =
        ProjectNodeEntity(
            id = id,
            projectId = 1L,
            parentId = parent,
            title = "T$id",
            sortOrder = order,
            estimatedMinutes = minutes,
            createdAt = 0L
        )

    @Test
    fun `range goes from start to expected end plus three days`() {
        val nodes = listOf(entity(1, minutes = 240))
        val plan = planProject(cfg, nodes.map { PlanNode(it.id, null, it.sortOrder, false, it.estimatedMinutes, 0, false, null, null) }, mon)
        val range = ganttRangeOf(plan, mon, null, mon)
        assertEquals(mon, range.fromDay)
        assertEquals(mon + 3, range.toDay)
        assertEquals(4L, range.days)
    }

    @Test
    fun `range includes the deadline when it is after the plan`() {
        val plan = planProject(cfg.copy(deadlineDate = mon + 10), listOf(PlanNode(1, null, 1, false, 60, 0, false, null, null)), mon)
        val range = ganttRangeOf(plan, mon, mon + 10, mon)
        assertEquals(mon + 13, range.toDay)
    }

    @Test
    fun `day to x uses the day width`() {
        val range = GanttRange(mon, mon + 10)
        assertEquals(0f, dayToX(mon, range, 8f), 0.001f)
        assertEquals(24f, dayToX(mon + 3, range, 8f), 0.001f)
        assertEquals(16f, spanToWidth(mon, mon + 1, 8f), 0.001f)
    }

    @Test
    fun `gantt rows follow the list order and keep critical flags`() {
        val nodes = listOf(entity(1), entity(2))
        val planNodes = listOf(
            PlanNode(1, null, 1, false, 60, 0, false, null, null, emptyList()),
            PlanNode(2, null, 2, false, 60, 0, false, null, null, listOf(1L))
        )
        val plan = planProject(cfg, planNodes, mon)
        val rows = buildGanttRows(nodes, dependencyMap(listOf(Pair(1L, 2L))), plan, null, mon, emptySet())
        assertEquals(listOf(1L, 2L), rows.map { it.nodeId })
        assertTrue(rows.all { it.critical })
        assertEquals(listOf(1L), rows[1].predecessorIds)
    }

    @Test
    fun `collapsed group hides its children in gantt rows`() {
        val nodes = listOf(entity(10, minutes = 0), entity(11, parent = 10), entity(12))
        val planNodes = listOf(
            PlanNode(10, null, 10, false, 0, 0, false, null, null, emptyList()),
            PlanNode(11, 10L, 11, false, 60, 0, false, null, null, emptyList()),
            PlanNode(12, null, 12, false, 60, 0, false, null, null, emptyList())
        )
        val plan = planProject(cfg, planNodes, mon)
        val rows = buildGanttRows(nodes, emptyMap(), plan, null, mon, setOf(10L))
        assertEquals(listOf(10L, 12L), rows.map { it.nodeId })
    }

    @Test
    fun `dependency map groups predecessors by successor`() {
        val map = dependencyMap(listOf(Pair(1L, 3L), Pair(2L, 3L), Pair(1L, 4L)))
        assertEquals(listOf(1L, 2L), map[3L])
        assertEquals(listOf(1L), map[4L])
    }
}
