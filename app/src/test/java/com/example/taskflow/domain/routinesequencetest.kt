package com.example.taskflow.domain

import com.example.taskflow.data.entities.RoutineTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSequenceTest {

    private fun task(id: Long, order: Int, optional: Boolean = false) =
        RoutineTaskEntity(id = id, routineId = 1L, title = "T$id", sortOrder = order, isOptional = optional)

    private val ordered = orderForSequence(listOf(task(1, 0), task(2, 1), task(3, 2)))

    @Test
    fun `non sequential routine never blocks`() {
        assertTrue(canToggleInSequence(false, ordered, emptySet(), 3L))
    }

    @Test
    fun `cannot complete a task before the previous ones`() {
        assertTrue(canToggleInSequence(true, ordered, emptySet(), 1L))
        assertFalse(canToggleInSequence(true, ordered, emptySet(), 2L))
        assertTrue(canToggleInSequence(true, ordered, setOf(1L), 2L))
    }

    @Test
    fun `can only undo the last completed task`() {
        assertFalse(canToggleInSequence(true, ordered, setOf(1L, 2L), 1L))
        assertTrue(canToggleInSequence(true, ordered, setOf(1L, 2L), 2L))
    }

    @Test
    fun `optional tasks go after mandatory ones`() {
        val mixed = orderForSequence(listOf(task(1, 0, optional = true), task(2, 1), task(3, 2)))
        assertEquals(listOf(2L, 3L, 1L), mixed.map { it.id })
        assertEquals(2L, firstPendingInSequence(mixed, emptySet()))
    }
}
