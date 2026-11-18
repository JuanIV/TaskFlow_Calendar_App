package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakLogicTest {

    private fun d(date: Long, status: String) = DayStatus(date, status)

    @Test
    fun `current streak counts completed and ignores paused grace neutral`() {
        val records = listOf(d(10, "COMPLETED"), d(9, "PAUSED"), d(8, "COMPLETED"), d(7, "FAILED"), d(6, "COMPLETED"))
        assertEquals(2, currentStreak(records, "PENDING"))
        assertEquals(3, currentStreak(records, "COMPLETED"))
        assertEquals(2, currentStreak(records, "NEUTRAL"))
    }

    @Test
    fun `failed yesterday gives zero`() {
        val records = listOf(d(10, "FAILED"), d(9, "COMPLETED"))
        assertEquals(0, currentStreak(records, "PENDING"))
        assertEquals(1, currentStreak(records, "COMPLETED"))
    }

    @Test
    fun `best streak finds maximum`() {
        val records = listOf(d(1, "COMPLETED"), d(2, "COMPLETED"), d(3, "FAILED"), d(4, "COMPLETED"),
            d(5, "COMPLETED"), d(6, "COMPLETED"), d(7, "FAILED"))
        assertEquals(3, bestStreak(records, "PENDING"))
    }

    @Test
    fun `best streak is never lower than current`() {
        val records = listOf(d(1, "COMPLETED"), d(2, "COMPLETED"), d(3, "COMPLETED"))
        assertEquals(4, bestStreak(records, "COMPLETED"))
    }

    @Test
    fun `compliance excludes paused and neutral`() {
        val records = listOf(d(1, "COMPLETED"), d(2, "FAILED"), d(3, "PAUSED"), d(4, "NEUTRAL"), d(5, "GRACE"), d(6, "COMPLETED"))
        assertEquals(50f, compliancePercent(records, 1, 6), 0.01f)
    }
}
