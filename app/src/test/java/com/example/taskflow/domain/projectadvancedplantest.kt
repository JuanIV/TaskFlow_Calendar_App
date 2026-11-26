package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAdvancedPlanTest {

    private fun node(id: Long, parent: Long? = null, completed: Boolean = false) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = parent,
        title = "T" + id,
        sortOrder = id.toInt(),
        completed = completed,
        createdAt = 0L
    )

    @Test
    fun `advanced plan with pending leaves has progress and no dates`() {
        val plan = advancedProjectPlan(100L, listOf(node(1, completed = true), node(2)))
        assertEquals(50, plan.progressPercent)
        assertEquals(PlanStatus.SIN_DEADLINE, plan.status)
        assertNull(plan.expectedEndDay)
        assertTrue(plan.parts.isEmpty())
        assertTrue(plan.nodes.isEmpty())
        assertEquals(0, plan.remainingMinutes)
    }

    @Test
    fun `advanced plan with everything done reports no pending work`() {
        val plan = advancedProjectPlan(100L, listOf(node(1, completed = true)))
        assertEquals(100, plan.progressPercent)
        assertEquals(PlanStatus.SIN_PENDIENTE, plan.status)
    }

    @Test
    fun `advanced summary mirrors the plan and has no today label`() {
        val summary = advancedProjectSummary(7L, listOf(node(1), node(2, completed = true)))
        assertEquals(7L, summary.projectId)
        assertEquals(50, summary.progressPercent)
        assertNull(summary.todayLabel)
        assertEquals(0, summary.todayExtraCount)
    }
}
