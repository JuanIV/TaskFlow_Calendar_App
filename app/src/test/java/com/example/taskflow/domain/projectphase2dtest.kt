package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectDailyLogEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectPhase2dTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()

    @Test
    fun `totals count planned leaves and completed leaves`() {
        val logs = listOf(
            ProjectDailyLogEntity(1L, mon, 60, 60),
            ProjectDailyLogEntity(2L, mon, 120, 30),
            ProjectDailyLogEntity(3L, mon, 0, 0)
        )
        val totals = totalsForDay(logs)
        assertEquals(2, totals.planned)
        assertEquals(1, totals.done)
    }

    @Test
    fun `done minutes are clamped to the planned minutes`() {
        assertEquals(60, clampedDone(60, 90))
        assertEquals(0, clampedDone(60, -5))
        assertEquals(30, clampedDone(60, 30))
    }

    @Test
    fun `merge keeps the larger done value`() {
        val existing = ProjectDailyLogEntity(1L, mon, 120, 60)
        val merged = mergeDailyLog(existing, 1L, mon, 120, 30)
        assertEquals(60, merged.doneMinutes)
        assertEquals(120, merged.plannedMinutes)
    }

    @Test
    fun `calendar items include deadline and milestones only`() {
        val items = projectCalendarItems("Web", mon + 5, listOf(Pair("Entrega", mon + 2)))
        assertEquals(2, items.size)
        assertTrue(items.first().isDeadline)
        assertEquals(mon + 5, items.first().day)
        assertEquals(false, items.last().isDeadline)
    }

    @Test
    fun `calendar items are empty without deadline or milestones`() {
        assertTrue(projectCalendarItems("Web", null, emptyList()).isEmpty())
    }

    @Test
    fun `depth counts ancestors`() {
        val nodes = listOf(
            ProjectNodeEntity(id = 1, projectId = 1, parentId = null, title = "A", sortOrder = 1, createdAt = 0),
            ProjectNodeEntity(id = 2, projectId = 1, parentId = 1, title = "B", sortOrder = 1, createdAt = 0),
            ProjectNodeEntity(id = 3, projectId = 1, parentId = 2, title = "C", sortOrder = 1, createdAt = 0)
        )
        assertEquals(0, depthOf(nodes, 1L))
        assertEquals(2, depthOf(nodes, 3L))
    }
}
