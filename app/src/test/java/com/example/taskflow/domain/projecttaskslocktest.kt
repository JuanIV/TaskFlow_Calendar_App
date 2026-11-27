package com.example.taskflow.domain

import com.example.taskflow.data.entities.TaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTasksLockTest {

    private fun t(id: Long, key: String?) = TaskEntity(
        id = id,
        title = "x",
        date = 5L,
        createdAt = 0L,
        projectId = if (key == null) null else 1L,
        projectNodeId = if (key == null) null else 1L,
        projectKey = key
    )

    @Test
    fun `part keys are recognised and other keys are not`() {
        assertTrue(isProjectPartKey("P1:N2:D5"))
        assertTrue(isProjectPartKey("P1:N2:D5~"))
        assertFalse(isProjectPartKey("P1:DAY5"))
        assertFalse(isProjectPartKey("P1:N2:C5"))
        assertFalse(isProjectPartKey("P1:N2:L"))
    }

    @Test
    fun `cell keys are recognised and other keys are not`() {
        assertTrue(isProjectCellKey("P1:N2:C5"))
        assertFalse(isProjectCellKey("P1:N2:D5"))
        assertFalse(isProjectCellKey("P1:DAY5"))
        assertFalse(isProjectCellKey("P1:N2:L"))
    }

    @Test
    fun `date lock applies to project tasks except loose ones`() {
        assertFalse(t(1, null).isProjectDateLocked())
        assertTrue(t(1, "P1:N2:D5").isProjectDateLocked())
        assertTrue(t(1, "P1:DAY5").isProjectDateLocked())
        assertFalse(t(1, "P1:N2:L").isProjectDateLocked())
        assertTrue(t(1, "P1:N2:L").isProjectTask())
        assertFalse(t(1, null).isProjectTask())
    }
}
