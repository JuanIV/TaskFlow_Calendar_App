package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectListTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()
    private val wed = mon + 2

    private fun node(
        id: Long,
        parent: Long? = null,
        minutes: Int = 0,
        order: Int,
        done: Int = 0,
        completed: Boolean = false,
        notBefore: Long? = null
    ) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = parent,
        title = "T$id",
        sortOrder = order,
        estimatedMinutes = minutes,
        doneMinutes = done,
        completed = completed,
        notBeforeDate = notBefore,
        createdAt = 0L
    )

    private fun pn(e: ProjectNodeEntity) = PlanNode(
        e.id, e.parentId, e.sortOrder, e.isMilestone, e.estimatedMinutes,
        e.doneMinutes, e.completed, e.notBeforeDate, e.pulledForwardDay
    )

    // 10 (grupo) -> 11 (120 min), 12 (60 min); 20 (60 min); 30 (60 min, no antes del miercoles)
    private fun baseNodes(completed11: Boolean = false) = listOf(
        node(10, order = 1),
        node(11, parent = 10, minutes = 120, order = 1, completed = completed11, done = if (completed11) 120 else 0),
        node(12, parent = 10, minutes = 60, order = 2),
        node(20, minutes = 60, order = 2),
        node(30, minutes = 60, order = 3, notBefore = wed)
    )

    private fun rowsFor(
        nodes: List<ProjectNodeEntity>,
        today: Long,
        filter: ListFilter,
        collapsed: Set<Long> = emptySet(),
        deadline: Long? = null
    ): List<ListRow> {
        val plan = planProject(
            PlanConfig(mon, deadline, 240, 31, emptySet(), false),
            nodes.map { pn(it) },
            today
        )
        return buildListRows(nodes, plan, deadline, today, filter, collapsed)
    }

    @Test
    fun `all filter shows every node in tree order with depth`() {
        val rows = rowsFor(baseNodes(), mon, ListFilter.ALL)
        assertEquals(listOf(10L, 11L, 12L, 20L, 30L), rows.map { it.nodeId })
        assertEquals(listOf(0, 1, 1, 0, 0), rows.map { it.depth })
        assertTrue(rows.first().isGroup)
    }

    @Test
    fun `collapsed group hides its children`() {
        val rows = rowsFor(baseNodes(), mon, ListFilter.ALL, collapsed = setOf(10L))
        assertEquals(listOf(10L, 20L, 30L), rows.map { it.nodeId })
        assertEquals(false, rows.first().expanded)
    }

    @Test
    fun `today filter keeps nodes with work today and their groups`() {
        val rows = rowsFor(baseNodes(), mon, ListFilter.TODAY)
        assertEquals(listOf(10L, 11L, 12L, 20L), rows.map { it.nodeId })
        assertEquals("T11", rows.first { it.nodeId == 11L }.todayLabel)
    }

    @Test
    fun `pending filter drops completed leaves and keeps groups with pending children`() {
        val rows = rowsFor(baseNodes(completed11 = true), mon, ListFilter.PENDING)
        assertEquals(listOf(10L, 12L, 20L, 30L), rows.map { it.nodeId })
    }

    @Test
    fun `late filter needs a deadline and marks nodes planned after it`() {
        assertTrue(rowsFor(baseNodes(), mon, ListFilter.LATE, deadline = null).isEmpty())
        val rows = rowsFor(baseNodes(), mon, ListFilter.LATE, deadline = mon - 1)
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.late })
    }

    @Test
    fun `summary gives status margin and today label of a project`() {
        val nodes = listOf(node(1, minutes = 480, order = 1))
        val summary = summarizeProject(
            projectId = 1L,
            config = PlanConfig(mon, mon + 2, 240, 31, emptySet(), false),
            nodes = nodes.map { pn(it) },
            titles = mapOf(1L to "Tarea"),
            today = mon
        )
        assertEquals(PlanStatus.JUSTO, summary.status)
        assertEquals("Tarea (parte 1/2)", summary.todayLabel)
        assertEquals(0, summary.todayExtraCount)
    }
}
