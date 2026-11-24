package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectPhase2eTest {

    private fun leaf(
        id: Long,
        estimated: Int,
        actual: Int?,
        completed: Boolean = true,
        milestone: Boolean = false
    ) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = null,
        title = "T$id",
        sortOrder = id.toInt(),
        isMilestone = milestone,
        estimatedMinutes = estimated,
        completed = completed,
        actualMinutes = actual,
        createdAt = 0L
    )

    @Test
    fun `deviation uses only completed leaves with actual time`() {
        val nodes = listOf(
            leaf(1, 60, 90),
            leaf(2, 60, null),
            leaf(3, 120, 60, completed = false),
            leaf(4, 0, 30, milestone = true)
        )
        val s = deviationSummary(nodes)
        assertEquals(1, s.sampleCount)
        assertEquals(50.0, s.deviationPercent, 0.001)
    }

    @Test
    fun `deviation is zero when there are no samples`() {
        val s = deviationSummary(listOf(leaf(1, 60, null)))
        assertEquals(0, s.sampleCount)
        assertEquals(0.0, s.deviationPercent, 0.001)
    }

    @Test
    fun `negative deviation is formatted with a minus sign`() {
        assertEquals("-8 %", formatDeviation(-8.0))
        assertEquals("+12 %", formatDeviation(12.4))
    }
}
