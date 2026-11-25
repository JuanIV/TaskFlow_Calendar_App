package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ProjectGridTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()
    private val tue = mon + 1
    private val wed = mon + 2
    private val days = GridDays(mon, mon + 6)

    private fun node(id: Long, parent: Long? = null, order: Int = id.toInt(), milestone: Boolean = false) =
        ProjectNodeEntity(
            id = id,
            projectId = 1L,
            parentId = parent,
            title = "T$id",
            sortOrder = order,
            isMilestone = milestone,
            estimatedMinutes = 60,
            createdAt = 0L
        )

    @Test
    fun `leaf marks exactly the planned days`() {
        val nodes = listOf(node(1))
        val rows = buildGridRows(
            nodes, days,
            mapOf(1L to mapOf(mon to 60, tue to 60)),
            emptySet(), emptySet()
        )
        val cells = rows.single().cells
        assertEquals(listOf(true, true, false, false, false, false, false), cells.map { it.planned })
    }

    @Test
    fun `group marks the union of its descendant days`() {
        val nodes = listOf(node(10), node(11, parent = 10), node(12, parent = 10))
        val rows = buildGridRows(
            nodes, days,
            mapOf(11L to mapOf(mon to 60), 12L to mapOf(wed to 60)),
            emptySet(), emptySet()
        )
        val group = rows.first { it.nodeId == 10L }
        assertTrue(group.isGroup)
        assertFalse(group.checkable)
        assertEquals(listOf(true, false, true, false, false, false, false), group.cells.map { it.planned })
    }

    @Test
    fun `root id is propagated to every row of the tree`() {
        val nodes = listOf(node(1), node(2, parent = 1), node(3, parent = 2), node(4))
        val rows = buildGridRows(nodes, days, emptyMap(), emptySet(), emptySet())
        assertEquals(listOf(1L, 1L, 1L, 4L), rows.map { it.rootId })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
    }

    @Test
    fun `collapsed group hides its children`() {
        val nodes = listOf(node(10), node(11, parent = 10))
        val rows = buildGridRows(nodes, days, emptyMap(), emptySet(), setOf(10L))
        assertEquals(listOf(10L), rows.map { it.nodeId })
    }

    @Test
    fun `completed leaf is completed only on planned days`() {
        val nodes = listOf(node(1))
        val rows = buildGridRows(
            nodes, days,
            mapOf(1L to mapOf(mon to 60)),
            setOf(1L), emptySet()
        )
        val cells = rows.single().cells
        assertTrue(cells[0].completed)
        assertFalse(cells[1].completed)
        assertTrue(rows.single().completed)
    }

    @Test
    fun `group is completed only when all its leaves are completed`() {
        val nodes = listOf(node(10), node(11, parent = 10), node(12, parent = 10))
        val rows = buildGridRows(
            nodes, days,
            mapOf(11L to mapOf(mon to 60), 12L to mapOf(mon to 60)),
            setOf(11L), emptySet()
        )
        assertFalse(rows.first { it.nodeId == 10L }.completed)
    }

    @Test
    fun `week aggregation marks the whole week when it has planned work`() {
        val cells = (0 until 7).map { i ->
            GridCell(mon + i, planned = i == 2, completed = false, minutes = if (i == 2) 60 else 0)
        }
        val weeks = weekCellsOf(cells, DayOfWeek.MONDAY)
        assertEquals(1, weeks.size)
        assertTrue(weeks.single().planned)
        assertEquals(60, weeks.single().minutes)
    }

    @Test
    fun `week start follows the configured first day`() {
        val sunday = LocalDate.of(2026, 11, 29).toEpochDay()
        assertEquals(mon, weekStartOfDay(sunday, DayOfWeek.MONDAY))
        assertEquals(sunday, weekStartOfDay(sunday, DayOfWeek.SUNDAY))
    }

    @Test
    fun `grid days cover start, expected end and deadline with margin`() {
        val g = gridDaysOf(mon, mon + 2, mon + 5, mon)
        assertEquals(mon, g.fromDay)
        assertEquals(mon + 8, g.toDay)
    }

    @Test
    fun `row colors get lighter with depth and are capped`() {
        val d0 = rowColor(0, 0)
        val d1 = rowColor(0, 1)
        val d9 = rowColor(0, 9)
        assertTrue(d1 != d0)
        assertTrue(brightness(d1) > brightness(d0))
        assertEquals(d9, rowColor(0, 4).let { _ -> rowColor(0, 9) })
        assertTrue(brightness(d9) <= brightness(lightenArgb(rootBaseColor(0), 1f)))
    }

    @Test
    fun `different roots get different base colors`() {
        assertFalse(rootBaseColor(0) == rootBaseColor(1))
    }

    @Test
    fun `completed cell is lighter than pending cell of the same row`() {
        val pending = cellColor(2, 1, completed = false)
        val done = cellColor(2, 1, completed = true)
        assertTrue(brightness(done) > brightness(pending))
    }

    private fun brightness(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (r + g + b) / 3
    }
}
