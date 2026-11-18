package com.example.taskflow.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockStateTest {

    @Test
    fun `disabled never locks`() {
        assertFalse(shouldLockAfterAbsence(false, 1000L, 999_999L, 30))
    }

    @Test
    fun `never left does not lock`() {
        assertFalse(shouldLockAfterAbsence(true, 0L, 999_999L, 30))
    }

    @Test
    fun `short absence does not lock`() {
        assertFalse(shouldLockAfterAbsence(true, 1000L, 11_000L, 30))
    }

    @Test
    fun `long absence locks`() {
        assertTrue(shouldLockAfterAbsence(true, 1000L, 40_000L, 30))
    }

    @Test
    fun `timeout zero locks at once`() {
        assertTrue(shouldLockAfterAbsence(true, 1000L, 1000L, 0))
    }
}