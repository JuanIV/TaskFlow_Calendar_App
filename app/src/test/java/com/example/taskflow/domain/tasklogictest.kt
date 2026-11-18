package com.example.taskflow.domain

import com.example.taskflow.data.entities.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TaskLogicTest {

    private fun day(d: Int) = LocalDate.of(2026, 11, d).toEpochDay()

    @Test
    fun `daily adds the interval`() {
        assertEquals(103L, nextTaskDate(100L, "DAILY", 3, 0))
    }

    @Test
    fun `weekly with mask goes to next marked day`() {
        val mask = 1 or 4 // lunes y miercoles
        assertEquals(day(4), nextTaskDate(day(2), "WEEKLY", 1, mask))
        assertEquals(day(9), nextTaskDate(day(4), "WEEKLY", 1, mask))
    }

    @Test
    fun `weekly with interval 2 skips a week`() {
        assertEquals(day(16), nextTaskDate(day(4), "WEEKLY", 2, 1 or 4))
    }

    @Test
    fun `monthly clamps to end of month`() {
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), nextTaskDate(LocalDate.of(2026, 1, 31).toEpochDay(), "MONTHLY", 1, 0))
    }

    @Test
    fun `none returns null`() {
        assertNull(nextTaskDate(100L, "NONE", 1, 0))
    }

    @Test
    fun `spawn date is always after today`() {
        val task = TaskEntity(title = "T", date = 95L, createdAt = 0L, recurrenceType = "DAILY")
        assertEquals(101L, nextSpawnDate(task, 100L))
    }

    @Test
    fun `task without date uses today as base`() {
        val task = TaskEntity(title = "T", date = null, createdAt = 0L, recurrenceType = "WEEKLY")
        assertEquals(107L, nextSpawnDate(task, 100L))
    }

    @Test
    fun `non recurring task has no spawn date`() {
        assertNull(nextSpawnDate(TaskEntity(title = "T", date = 100L, createdAt = 0L), 100L))
    }
}
