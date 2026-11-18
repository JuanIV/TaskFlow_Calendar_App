package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalDateTest {

    @Test
    fun `logical offsets are calculated correctly`() {
        // Si el dia empieza a las 04:00 (240 min)
        val dayStartHour = 4

        // Las 04:00 es el minuto 0 logico
        assertEquals(0, getLogicalMinuteOffset(240, dayStartHour))

        // Las 05:00 (300 min) es el minuto 60 logico
        assertEquals(60, getLogicalMinuteOffset(300, dayStartHour))

        // Las 03:59 (239 min) es el ultimo minuto del dia logico (1439)
        assertEquals(1439, getLogicalMinuteOffset(239, dayStartHour))
    }

    @Test
    fun `window validation works across midnight`() {
        val dayStartHour = 4

        // Ventana de 22:00 (1320) a 02:00 (120) cruzando medianoche
        val start = 1320
        val end = 120

        // En logica normal 120 no es mayor que 1320, pero en nuestro offset logico si
        assertTrue(isValidLogicalWindow(start, end, dayStartHour))

        // Las 01:00 (60) esta dentro
        assertTrue(isMinuteInLogicalWindow(60, start, end, dayStartHour))

        // Las 23:00 (1380) esta dentro
        assertTrue(isMinuteInLogicalWindow(1380, start, end, dayStartHour))

        // Las 20:00 (1200) esta fuera
        assertFalse(isMinuteInLogicalWindow(1200, start, end, dayStartHour))

        // Las 03:00 (180) esta fuera
        assertFalse(isMinuteInLogicalWindow(180, start, end, dayStartHour))
    }
}
