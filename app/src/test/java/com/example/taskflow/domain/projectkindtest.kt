package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectKindTest {

    @Test
    fun `null and known kinds are valid`() {
        assertTrue(isValidProjectKind(null))
        assertTrue(isValidProjectKind(ProjectKind.SIMPLE))
        assertTrue(isValidProjectKind(ProjectKind.ADVANCED))
        assertFalse(isValidProjectKind("OTHER"))
    }

    @Test
    fun `effective kind defaults to simple`() {
        assertEquals(ProjectKind.SIMPLE, effectiveProjectKind(null))
        assertEquals(ProjectKind.SIMPLE, effectiveProjectKind("OTHER"))
        assertEquals(ProjectKind.ADVANCED, effectiveProjectKind(ProjectKind.ADVANCED))
    }
}
