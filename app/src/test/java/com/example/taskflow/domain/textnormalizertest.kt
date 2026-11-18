package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `removes accents and lowercases`() {
        assertEquals("reunion", normalizeText("Reuni\u00F3n"))
        assertTrue(normalizeText("Reuni\u00F3n de equipo").contains(normalizeText("REUNION")))
    }
}
