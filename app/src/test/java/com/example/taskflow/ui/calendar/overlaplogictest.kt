package com.example.taskflow.ui.calendar

import com.example.taskflow.domain.EventOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlapLogicTest {

    private fun occ(start: Int?, end: Int?, id: Long = 1, allDay: Boolean = false) = EventOccurrence(
        eventId = id, originalDate = 100L, startDate = 100L, endDate = null, allDay = allDay,
        startMinute = start, endMinute = end, title = "T$id", colorArgb = 0,
        notes = null, location = null, isException = false
    )

    @Test
    fun `overlapping events share columns`() {
        val packed = packEvents(listOf(occ(600, 700, 1), occ(630, 720, 2)))
        assertEquals(2, packed.size)
        assertTrue(packed.all { it.totalColumns == 2 })
        assertEquals(setOf(0, 1), packed.map { it.column }.toSet())
    }

    @Test
    fun `non overlapping events use one column`() {
        val packed = packEvents(listOf(occ(600, 660, 1), occ(660, 720, 2)))
        assertTrue(packed.all { it.totalColumns == 1 })
    }

    @Test
    fun `event without end lasts 30 minutes`() {
        assertTrue(packEvents(listOf(occ(600, null, 1), occ(620, 700, 2))).all { it.totalColumns == 2 })
        assertTrue(packEvents(listOf(occ(600, null, 1), occ(630, 700, 2))).all { it.totalColumns == 1 })
    }

    @Test
    fun `all day events are excluded`() {
        assertEquals(0, packEvents(listOf(occ(null, null, 1, allDay = true))).size)
    }
}
