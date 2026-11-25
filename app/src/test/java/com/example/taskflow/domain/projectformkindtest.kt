package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectFormKindTest {

    @Test
    fun `form defaults to simple kind`() {
        val form = ProjectFormData(
            name = "A",
            description = null,
            colorArgb = 0,
            icon = null,
            startDate = 0L,
            deadlineDate = null,
            capacityMinutes = 240,
            workingDaysMask = 31,
            excludedDays = emptyList()
        )
        assertEquals(ProjectKind.SIMPLE, form.kind)
    }

    @Test
    fun `advanced kind is kept by the form`() {
        val form = ProjectFormData(
            name = "A",
            description = null,
            colorArgb = 0,
            icon = null,
            startDate = 0L,
            deadlineDate = null,
            capacityMinutes = 240,
            workingDaysMask = 31,
            excludedDays = emptyList(),
            kind = ProjectKind.ADVANCED
        )
        assertEquals(ProjectKind.ADVANCED, effectiveProjectKind(form.kind))
    }
}
