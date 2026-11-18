package com.example.taskflow.domain

import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventExceptionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EventRecurrenceExtraTest {

    private val start = LocalDate.of(2026, 11, 2)

    private fun base(type: String) = EventEntity(
        id = 1, title = "T", date = start.toEpochDay(), colorArgb = 0, createdAt = 0L, recurrenceType = type
    )

    @Test
    fun `until is inclusive`() {
        val event = base("DAILY").copy(recurrenceEndType = "UNTIL", recurrenceUntil = start.plusDays(2).toEpochDay())
        val occ = expandEvents(listOf(event), emptyList(), start.toEpochDay(), start.plusDays(30).toEpochDay())
        assertEquals(3, occ.size)
    }

    @Test
    fun `count is ignored when end type is never`() {
        val event = base("DAILY").copy(recurrenceCount = 2)
        val occ = expandEvents(listOf(event), emptyList(), start.toEpochDay(), start.plusDays(9).toEpochDay())
        assertEquals(10, occ.size)
    }

    @Test
    fun `count limits occurrences`() {
        val event = base("DAILY").copy(recurrenceEndType = "COUNT", recurrenceCount = 4)
        val occ = expandEvents(listOf(event), emptyList(), start.toEpochDay(), start.plusDays(30).toEpochDay())
        assertEquals(4, occ.size)
    }

    @Test
    fun `exception overrides importance`() {
        val event = base("DAILY").copy(recurrenceEndType = "COUNT", recurrenceCount = 2)
        val ex = EventExceptionEntity(
            eventId = 1, originalDate = start.toEpochDay(), isDeleted = false, overrideTitle = "X",
            overrideDate = start.toEpochDay(), overrideEndDate = null, overrideAllDay = true,
            overrideStartMinute = null, overrideEndMinute = null, overrideColorArgb = 0,
            overrideNotes = null, overrideLocation = null, overrideImportance = "SECONDARY"
        )
        val occ = expandEvents(listOf(event), listOf(ex), start.toEpochDay(), start.plusDays(5).toEpochDay())
        assertEquals("SECONDARY", occ[0].priority)
        assertEquals("IMPORTANT", occ[1].priority)
    }

    @Test
    fun `next reminder returns the original date of the next occurrence`() {
        val zone = ZoneId.systemDefault()
        val event = base("WEEKLY").copy(allDay = true, recurrenceWeekdaysMask = 1)
        val now = start.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val t = nextReminderOccurrence(event, emptyList(), 0, now)
        assertEquals(start.plusDays(7).toEpochDay(), t?.occurrenceOriginalDate)
    }
}
