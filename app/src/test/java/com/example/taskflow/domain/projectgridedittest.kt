package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectGridEditTest {

    private fun node(id: Long, parent: Long? = null, milestone: Boolean = false) =
        ProjectNodeEntity(
            id = id,
            projectId = 1L,
            parentId = parent,
            title = "T$id",
            sortOrder = id.toInt(),
            isMilestone = milestone,
            estimatedMinutes = 60,
            createdAt = 0L
        )

    @Test
    fun `assignments convert to a leaf planned map with 60 minutes per day`() {
        val map = assignmentsToLeafPlanned(setOf(Pair(1L, 100L), Pair(1L, 101L), Pair(2L, 100L)))
        assertEquals(mapOf(100L to 60, 101L to 60), map[1L])
        assertEquals(mapOf(100L to 60), map[2L])
    }

    @Test
    fun `a leaf node is assignable`() {
        val nodes = listOf(node(1))
        assertTrue(isAssignableLeaf(nodes, 1L))
    }

    @Test
    fun `a group node is not assignable`() {
        val nodes = listOf(node(10), node(11, parent = 10))
        assertFalse(isAssignableLeaf(nodes, 10L))
    }

    @Test
    fun `a milestone is not assignable`() {
        val nodes = listOf(node(1, milestone = true))
        assertFalse(isAssignableLeaf(nodes, 1L))
    }

    @Test
    fun `an unknown node is not assignable`() {
        assertFalse(isAssignableLeaf(emptyList(), 99L))
    }
}
