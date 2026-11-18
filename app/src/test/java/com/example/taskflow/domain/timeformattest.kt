package com.example.taskflow.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @After
    fun reset() {
        TimeFormatState.use24h = true
    }

    @Test
    fun `formats 24 hours`() {
        TimeFormatState.use24h = true
        assertEquals("09:05", formatMinute(9 * 60 + 5))
        assertEquals("00:00", formatMinute(0))
    }

    @Test
    fun `formats 12 hours`() {
        TimeFormatState.use24h = false
        assertEquals("12:00 AM", formatMinute(0))
        assertEquals("1:05 PM", formatMinute(13 * 60 + 5))
        assertEquals("12:30 PM", formatMinute(12 * 60 + 30))
    }
}
