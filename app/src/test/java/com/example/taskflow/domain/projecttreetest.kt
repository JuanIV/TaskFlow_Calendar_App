package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTreeTest {

    private fun node(
        id: Long,
        parent: Long? = null,
        minutes: Int = 0,
        milestone: Boolean = false,
        done: Int = 0,
        completed: Boolean = false,
        order: Int = id.toInt()
    ) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = parent,
        title = "T$id",
        sortOrder = order,
        isMilestone = milestone,
        estimatedMinutes = minutes,
        doneMinutes = done,
        completed = completed,
        createdAt = 0L
    )

    private fun draft(minutes: Int, milestone: Boolean = false, title: String = "X") =
        NodeDraft(title, null, minutes, milestone, 0, null, null)

    @Test
    fun `hours are parsed with comma or dot`() {
        assertEquals(90, parseHoursToMinutes("1,5"))
        assertEquals(90, parseHoursToMinutes("1.5"))
        assertEquals(120, parseHoursToMinutes(" 2 "))
        assertEquals(0, parseHoursToMinutes("0"))
        assertNull(parseHoursToMinutes("abc"))
        assertNull(parseHoursToMinutes("-1"))
        assertNull(parseHoursToMinutes(""))
    }

    @Test
    fun `hours are formatted without useless zeros`() {
        assertEquals("4", formatHours(240))
        assertEquals("1.5", formatHours(90))
        assertEquals("0.25", formatHours(15))
    }

    @Test
    fun `picker millis roundtrip keeps the day`() {
        val day = 20_000L
        assertEquals(day, pickerMillisToEpochDay(epochDayToPickerMillis(day)))
    }

    @Test
    fun `status transitions follow the rules`() {
        assertTrue(canTransitionProjectStatus(ProjectStatus.ACTIVE, ProjectStatus.PAUSED))
        assertTrue(canTransitionProjectStatus(ProjectStatus.PAUSED, ProjectStatus.ACTIVE))
        assertTrue(canTransitionProjectStatus(ProjectStatus.ACTIVE, ProjectStatus.COMPLETED))
        assertTrue(canTransitionProjectStatus(ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED))
        assertTrue(canTransitionProjectStatus(ProjectStatus.ARCHIVED, ProjectStatus.ACTIVE))
        assertFalse(canTransitionProjectStatus(ProjectStatus.ARCHIVED, ProjectStatus.PAUSED))
        assertFalse(canTransitionProjectStatus(ProjectStatus.COMPLETED, ProjectStatus.PAUSED))
        assertFalse(canTransitionProjectStatus(ProjectStatus.ACTIVE, ProjectStatus.ACTIVE))
    }

    @Test
    fun `project validation reports the first problem`() {
        assertEquals(OpResult.EMPTY_NAME, validateProject("  ", 240, 31, 10L, null))
        assertEquals(OpResult.INVALID_CAPACITY, validateProject("A", 0, 31, 10L, null))
        assertEquals(OpResult.INVALID_CAPACITY, validateProject("A", 2000, 31, 10L, null))
        assertEquals(OpResult.INVALID_DAYS, validateProject("A", 240, 0, 10L, null))
        assertEquals(OpResult.INVALID_DEADLINE, validateProject("A", 240, 31, 10L, 9L))
        assertEquals(OpResult.OK, validateProject("A", 240, 31, 10L, 10L))
    }

    @Test
    fun `node draft validation`() {
        assertEquals(OpResult.EMPTY_TITLE, validateNodeDraft(draft(60, title = " "), true))
        assertEquals(OpResult.INVALID_MINUTES, validateNodeDraft(draft(0), true))
        assertEquals(OpResult.OK, validateNodeDraft(draft(0, milestone = true), true))
        assertEquals(OpResult.OK, validateNodeDraft(draft(0), false))
        assertEquals(OpResult.OK, validateNodeDraft(draft(60), true))
    }

    @Test
    fun `subtree contains the node and all its descendants`() {
        val nodes = listOf(node(1), node(2, parent = 1), node(3, parent = 2), node(4))
        assertEquals(setOf(1L, 2L, 3L), subtreeIds(nodes, 1L))
        assertEquals(setOf(4L), subtreeIds(nodes, 4L))
    }

    @Test
    fun `insertAt places the node at the requested index`() {
        assertEquals(listOf(2L, 1L, 3L), insertAt(listOf(2L, 3L), 1L, 1))
        assertEquals(listOf(1L, 2L, 3L), insertAt(listOf(2L, 3L), 1L, 0))
        assertEquals(listOf(2L, 3L, 1L), insertAt(listOf(2L, 3L), 1L, 99))
        assertEquals(listOf(2L, 3L, 1L), insertAt(listOf(1L, 2L, 3L), 1L, 2))
    }

    @Test
    fun `resizing a completed leaf keeps it completed`() {
        val n = resizeLeaf(node(1, minutes = 60, done = 60, completed = true), 120, 5L)
        assertEquals(120, n.doneMinutes)
        assertTrue(n.completed)
    }

    @Test
    fun `shrinking below progress completes the leaf`() {
        val n = resizeLeaf(node(1, minutes = 120, done = 60), 30, 5L)
        assertEquals(30, n.doneMinutes)
        assertTrue(n.completed)
        assertEquals(5L, n.completedAt)
    }

    @Test
    fun `enlarging a partial leaf keeps its progress`() {
        val n = resizeLeaf(node(1, minutes = 120, done = 60), 90, 5L)
        assertEquals(60, n.doneMinutes)
        assertFalse(n.completed)
        assertNull(n.completedAt)
    }

    @Test
    fun `milestone conversion clears duration and progress`() {
        val m = toMilestone(node(1, minutes = 120, done = 60))
        assertTrue(m.isMilestone)
        assertEquals(0, m.estimatedMinutes)
        assertEquals(0, m.doneMinutes)
        assertFalse(m.completed)
    }

    @Test
    fun `group converted back to leaf gets default duration`() {
        val g = groupToLeaf(node(1))
        assertEquals(DEFAULT_LEAF_MINUTES, g.estimatedMinutes)
        assertFalse(g.isMilestone)
    }

    @Test
    fun `leaf with progress cannot become a group`() {
        val nodes = listOf(node(1, minutes = 60, done = 30))
        assertEquals(OpResult.PARENT_HAS_PROGRESS, checkCanHaveChildren(nodes, nodes[0]))
    }

    @Test
    fun `milestone cannot have children`() {
        val nodes = listOf(node(1, milestone = true))
        assertEquals(OpResult.PARENT_IS_MILESTONE, checkCanHaveChildren(nodes, nodes[0]))
    }

    @Test
    fun `group with children is always allowed as parent`() {
        val nodes = listOf(node(1, minutes = 60, done = 30), node(2, parent = 1, minutes = 60))
        assertEquals(OpResult.OK, checkCanHaveChildren(nodes, nodes[0]))
    }

    @Test
    fun `progress is detected anywhere in the subtree`() {
        val nodes = listOf(
            node(1),
            node(2, parent = 1, minutes = 60, done = 10),
            node(3, parent = 1, minutes = 60)
        )
        assertTrue(subtreeHasProgress(nodes, 1L))
        assertFalse(subtreeHasProgress(nodes, 3L))
    }

    @Test
    fun `move targets exclude the node subtree and milestones`() {
        val nodes = listOf(
            node(1),
            node(2, parent = 1, minutes = 60),
            node(3, minutes = 60),
            node(4, milestone = true)
        )
        assertEquals(listOf(3L), moveTargetsFor(nodes, 1L).map { it.id })
    }
}
