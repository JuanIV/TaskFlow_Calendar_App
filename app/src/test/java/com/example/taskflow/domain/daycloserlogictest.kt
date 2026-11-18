package com.example.taskflow.domain

import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayCloserLogicTest {

    private fun routine(grace: Int = 0, paused: Boolean = false) = RoutineEntity(
        id = 1, name = "R", colorArgb = 0, recurrenceType = "EVERY_N_DAYS", weekdaysMask = 0,
        intervalDays = 1, anchorDate = 0L, startMinuteOfDay = 0, createdAt = 0L,
        graceDaysPerMonth = grace,
        pausedFromEpochDay = if (paused) 0L else null,
        pausedUntilEpochDay = null
    )

    private fun task(id: Long, optional: Boolean = false) =
        RoutineTaskEntity(id = id, routineId = 1, title = "T$id", sortOrder = 0, isOptional = optional)

    @Test
    fun `all required done is completed`() {
        val r = evaluateRoutineDay(routine(), listOf(task(1), task(2)), setOf(1L, 2L), 10, 0)
        assertEquals("COMPLETED", r?.status)
        assertEquals(2, r?.required)
    }

    @Test
    fun `optional tasks are ignored when there are mandatory ones`() {
        val r = evaluateRoutineDay(routine(), listOf(task(1), task(2, optional = true)), setOf(1L), 10, 0)
        assertEquals("COMPLETED", r?.status)
        assertEquals(1, r?.required)
    }

    @Test
    fun `without mandatory tasks all are required`() {
        val r = evaluateRoutineDay(routine(), listOf(task(1, true), task(2, true)), setOf(1L), 10, 0)
        assertEquals("FAILED", r?.status)
        assertEquals(2, r?.required)
    }

    @Test
    fun `routine without tasks has no record`() {
        assertNull(evaluateRoutineDay(routine(), emptyList(), emptySet(), 10, 0))
    }

    @Test
    fun `grace days are consumed`() {
        assertEquals("GRACE", evaluateRoutineDay(routine(grace = 1), listOf(task(1)), emptySet(), 10, 0)?.status)
        assertEquals("FAILED", evaluateRoutineDay(routine(grace = 1), listOf(task(1)), emptySet(), 10, 1)?.status)
    }

    @Test
    fun `paused routine gives paused record`() {
        assertEquals("PAUSED", evaluateRoutineDay(routine(paused = true), listOf(task(1)), emptySet(), 10, 0)?.status)
    }

    @Test
    fun `global day is neutral when only paused`() {
        val g = evaluateGlobalDay(listOf(RoutineDayResult(1, "PAUSED", 0, 0)), 0, 0)
        assertEquals("NEUTRAL", g.status)
    }

    @Test
    fun `global day fails when a routine failed`() {
        val g = evaluateGlobalDay(listOf(RoutineDayResult(1, "FAILED", 2, 1)), 0, 0)
        assertEquals("FAILED", g.status)
        assertEquals(1, g.done)
    }

    @Test
    fun `global day completed adds routines and tasks`() {
        val g = evaluateGlobalDay(listOf(RoutineDayResult(1, "COMPLETED", 2, 2)), 3, 3)
        assertEquals("COMPLETED", g.status)
        assertEquals(5, g.required)
        assertEquals(5, g.done)
    }
}
