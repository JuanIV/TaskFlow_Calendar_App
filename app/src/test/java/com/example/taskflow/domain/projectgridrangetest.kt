package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ProjectGridRangeTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()
    private val days = GridDays(mon, mon + 6)

    private fun node(id: Long, parent: Long? = null) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = parent,
        title = "T" + id,
        sortOrder = id.toInt(),
        createdAt = 0L
    )

    @Test
    fun `initial range covers a week before today and eight weeks ahead in whole weeks`() {
        val g = initialGridDays(mon, listOf(mon + 1), null, mon, DayOfWeek.MONDAY)
        assertEquals(mon - 7, g.fromDay)
        assertEquals(mon + 62, g.toDay)
        assertEquals(0, g.size % 7)
    }

    @Test
    fun `initial range grows to include deadline and far cells`() {
        val withDeadline = initialGridDays(mon, emptyList(), mon + 100, mon, DayOfWeek.MONDAY)
        assertEquals(mon + 104, withDeadline.toDay)
        val withOldCell = initialGridDays(mon, listOf(mon - 30), null, mon, DayOfWeek.MONDAY)
        assertEquals(mon - 35, withOldCell.fromDay)
        val withFarCell = initialGridDays(mon, listOf(mon + 200), null, mon, DayOfWeek.MONDAY)
        assertTrue(withFarCell.toDay >= mon + 214)
    }

    @Test
    fun `extension adds days on both sides`() {
        val g = extendedGridDays(GridDays(10, 20), 28, 14)
        assertEquals(-18L, g.fromDay)
        assertEquals(34L, g.toDay)
    }

    @Test
    fun `week numbers count from the origin week`() {
        assertEquals(0, weekNumberOf(mon, mon))
        assertEquals(2, weekNumberOf(mon + 14, mon))
        assertEquals(-1, weekNumberOf(mon - 7, mon))
    }

    @Test
    fun `completed leaf is done on all its planned days`() {
        val rows = buildGridRows(
            listOf(node(1)), days,
            mapOf(1L to mapOf(mon to 60, mon + 1 to 60, mon + 2 to 60)),
            setOf(1L), emptySet(),
            mapOf(1L to mon + 1), emptySet()
        )
        assertEquals(listOf(true, true, true), rows.single().cells.take(3).map { it.completed })
    }

    @Test
    fun `completed task of a cell marks only that day`() {
        val rows = buildGridRows(
            listOf(node(1)), days,
            mapOf(1L to mapOf(mon to 60, mon + 1 to 60)),
            emptySet(), emptySet(),
            emptyMap(), setOf(Pair(1L, mon))
        )
        val cells = rows.single().cells
        assertTrue(cells[0].completed)
        assertFalse(cells[1].completed)
        assertFalse(rows.single().completed)
    }

    @Test
    fun `group day is done only when every leaf with a cell that day is done`() {
        val nodes = listOf(node(10), node(11, 10), node(12, 10))
        val planned = mapOf(11L to mapOf(mon to 60), 12L to mapOf(mon to 60))
        val partial = buildGridRows(nodes, days, planned, emptySet(), emptySet(), emptyMap(), setOf(Pair(11L, mon)))
        assertFalse(partial.first { it.nodeId == 10L }.cells[0].completed)
        val full = buildGridRows(nodes, days, planned, emptySet(), emptySet(), emptyMap(), setOf(Pair(11L, mon), Pair(12L, mon)))
        assertTrue(full.first { it.nodeId == 10L }.cells[0].completed)
    }
}
