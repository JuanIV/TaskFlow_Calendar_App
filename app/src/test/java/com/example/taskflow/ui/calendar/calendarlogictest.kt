package com.example.taskflow.ui.calendar

import com.example.taskflow.domain.EventOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarLogicTest {

    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun `month pages round trip`() {
        val page = pageForDate("MONTH", today, LocalDate.of(2027, 1, 20))
        assertEquals(PAGER_CENTER + 2, page)
        assertEquals(LocalDate.of(2027, 1, 1), dateForPage("MONTH", today, page))
    }

    @Test
    fun `week pages use monday`() {
        val page = pageForDate("WEEK", today, LocalDate.of(2026, 11, 17))
        assertEquals(PAGER_CENTER + 1, page)
        assertEquals(LocalDate.of(2026, 11, 16), dateForPage("WEEK", today, page))
    }

    @Test
    fun `day pages are consecutive days`() {
        assertEquals(PAGER_CENTER + 6, pageForDate("DAY", today, LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun `multi day events appear on every day`() {
        val occ = EventOccurrence(
            eventId = 1, originalDate = 100L, startDate = 100L, endDate = 102L, allDay = true,
            startMinute = null, endMinute = null, title = "T", colorArgb = 0,
            notes = null, location = null, isException = false
        )
        val map = groupByDay(listOf(occ))
        assertEquals(setOf(100L, 101L, 102L), map.keys)
        assertTrue(occ.isStripEvent())
    }
}
