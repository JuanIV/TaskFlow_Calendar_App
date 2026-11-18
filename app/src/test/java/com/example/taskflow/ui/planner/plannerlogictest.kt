package com.example.taskflow.ui.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannerLogicTest {

    @Test
    fun `first and second column`() {
        assertEquals(0, plannerColumnIndex(10f, 8f, 140f, 8f, 8))
        assertEquals(1, plannerColumnIndex(170f, 8f, 140f, 8f, 8))
    }

    @Test
    fun `left of padding is outside`() {
        assertNull(plannerColumnIndex(4f, 8f, 140f, 8f, 8))
    }

    @Test
    fun `beyond last column is outside`() {
        assertNull(plannerColumnIndex(8f + 148f * 8f + 1f, 8f, 140f, 8f, 8))
    }
}
