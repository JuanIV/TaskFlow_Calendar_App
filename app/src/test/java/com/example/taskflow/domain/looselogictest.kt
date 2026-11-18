package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LooseLogicTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(day: Int, hour: Int) =
        LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `returns next candidate today`() {
        assertEquals(ms(14, 15), nextLooseReminderTrigger(ms(14, 10), 9 * 60, 21 * 60, 6, zone))
    }

    @Test
    fun `goes to tomorrow after window`() {
        assertEquals(ms(15, 9), nextLooseReminderTrigger(ms(14, 22), 9 * 60, 21 * 60, 6, zone))
    }

    @Test
    fun `exact candidate time is skipped`() {
        assertEquals(ms(14, 21), nextLooseReminderTrigger(ms(14, 15), 9 * 60, 21 * 60, 6, zone))
    }
}
