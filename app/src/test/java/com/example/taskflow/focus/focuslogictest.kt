package com.example.taskflow.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusLogicTest {

    private val config = PomodoroConfig(25, 5, 15, 4)

    @Test
    fun `focus leads to short break until the last cycle`() {
        assertEquals(NextPhase("SHORT_BREAK", 1), nextPomodoroPhase("FOCUS", 0, config))
        assertEquals(NextPhase("SHORT_BREAK", 3), nextPomodoroPhase("FOCUS", 2, config))
    }

    @Test
    fun `fourth focus leads to long break`() {
        assertEquals(NextPhase("LONG_BREAK", 4), nextPomodoroPhase("FOCUS", 3, config))
    }

    @Test
    fun `breaks lead back to focus`() {
        assertEquals(NextPhase("FOCUS", 2), nextPomodoroPhase("SHORT_BREAK", 2, config))
        assertEquals(NextPhase("FOCUS", 0), nextPomodoroPhase("LONG_BREAK", 4, config))
    }

    @Test
    fun `phase minutes follow the config`() {
        assertEquals(25, phaseMinutes("FOCUS", config))
        assertEquals(5, phaseMinutes("SHORT_BREAK", config))
        assertEquals(15, phaseMinutes("LONG_BREAK", config))
    }
}
