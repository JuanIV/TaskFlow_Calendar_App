package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementLogicTest {

    @Test
    fun `nothing unlocked with zero stats`() {
        assertTrue(unlockedAchievementIds(AchievementStats(0, 0, 0, 0, 0, 0)).isEmpty())
    }

    @Test
    fun `task levels are cumulative`() {
        val ids = unlockedAchievementIds(AchievementStats(100, 0, 0, 0, 0, 0))
        assertTrue("TASKS_10" in ids)
        assertTrue("TASKS_50" in ids)
        assertTrue("TASKS_100" in ids)
        assertFalse("TASKS_500" in ids)
    }

    @Test
    fun `focus and first items`() {
        val ids = unlockedAchievementIds(AchievementStats(0, 0, 60, 0, 1, 1))
        assertEquals(setOf("FOCUS_60", "ROUTINE_1", "EVENT_1"), ids)
    }

    @Test
    fun `streak levels`() {
        val ids = unlockedAchievementIds(AchievementStats(0, 7, 0, 0, 0, 0))
        assertEquals(setOf("STREAK_3", "STREAK_7"), ids)
    }
}
