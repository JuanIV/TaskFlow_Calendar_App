package com.example.taskflow.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietLogicTest {

    @Test
    fun `quiet hours crossing logical midnight`() {
        // Silencio de 22:00 (1320) a 07:00 (420), el día empieza a las 04:00 (240)
        val dayStart = 4
        val start = 1320
        val end = 420

        // offStart = (1320 - 240 + 1440) % 1440 = 1080
        // offEnd = (420 - 240 + 1440) % 1440 = 180

        assertTrue(isQuietNow(1400, start, end, dayStart)) // 23:20
        assertTrue(isQuietNow(60, start, end, dayStart)) // 01:00
        assertFalse(isQuietNow(600, start, end, dayStart)) // 10:00
    }

    @Test
    fun `quiet hours within logical day`() {
        // Silencio de 14:00 (840) a 18:00 (1080), el día empieza a las 00:00
        assertTrue(isQuietNow(900, 840, 1080, 0)) // 15:00
        assertFalse(isQuietNow(1200, 840, 1080, 0)) // 20:00
    }
}
