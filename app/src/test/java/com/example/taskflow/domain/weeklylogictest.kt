package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WeeklyLogicTest {

    private val zone = ZoneId.of("UTC")

    private fun ms(day: Int, hour: Int) =
        LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `next sunday evening`() {
        assertEquals(ms(22, 20), nextWeeklyTrigger(ms(18, 10), 7, 20 * 60, zone))
    }

    @Test
    fun `same day later hour`() {
        assertEquals(ms(18, 12), nextWeeklyTrigger(ms(18, 10), 3, 12 * 60, zone))
    }

    @Test
    fun `same day earlier hour goes to next week`() {
        assertEquals(ms(25, 9), nextWeeklyTrigger(ms(18, 10), 3, 9 * 60, zone))
    }
}
