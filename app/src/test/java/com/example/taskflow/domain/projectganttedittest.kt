package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProjectGanttEditTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()

    @Test
    fun `moving two columns to the right gives two days later`() {
        assertEquals(mon + 2, dayAfterMove(mon, 16f, 8f, mon))
    }

    @Test
    fun `moving before today is clamped to today`() {
        assertEquals(mon, dayAfterMove(mon + 3, -40f, 8f, mon))
    }

    @Test
    fun `small drags below half a day do not move the bar`() {
        assertEquals(mon, dayAfterMove(mon, 3f, 8f, mon))
    }

    @Test
    fun `bar length is at least one day`() {
        assertEquals(1L, barDays(mon, mon - 3))
        assertEquals(3L, barDays(mon, mon + 2))
    }

    @Test
    fun `resizing one day longer scales minutes proportionally`() {
        // 240 min en 1 dia -> 2 dias = 480 min
        assertEquals(480, minutesAfterResize(240, 1L, 8f, 8f))
    }

    @Test
    fun `resizing shorter never goes below one hour`() {
        assertEquals(60, minutesAfterResize(120, 1L, -80f, 8f))
    }

    @Test
    fun `resizing with zero width change keeps minutes`() {
        assertEquals(240, minutesAfterResize(240, 2L, 0f, 8f))
    }
}
