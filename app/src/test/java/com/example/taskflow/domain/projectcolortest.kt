package com.example.taskflow.domain

import com.example.taskflow.data.entities.ProjectNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectColorTest {

    private val rootColor = 0xFF112233.toInt()

    private fun node(id: Long, parent: Long? = null, color: Int? = null) = ProjectNodeEntity(
        id = id,
        projectId = 1L,
        parentId = parent,
        title = "T" + id,
        sortOrder = id.toInt(),
        colorArgb = color,
        createdAt = 0L
    )

    private fun brightness(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (r + g + b) / 3
    }

    @Test
    fun `rows carry the color of their root and ignore their own`() {
        val nodes = listOf(node(1, color = rootColor), node(2, parent = 1, color = 0xFFFF0000.toInt()), node(3))
        val rows = buildGridRows(nodes, GridDays(0, 6), emptyMap(), emptySet(), emptySet())
        assertEquals(listOf(rootColor, rootColor), rows.take(2).map { it.rootColorArgb })
        assertNull(rows.last().rootColorArgb)
    }

    @Test
    fun `root color is used at depth zero and lighter below`() {
        assertEquals(rootColor, rowColorFor(rootColor, 0, 0))
        assertTrue(brightness(rowColorFor(rootColor, 0, 2)) > brightness(rowColorFor(rootColor, 0, 0)))
    }

    @Test
    fun `without a chosen color the automatic palette is used`() {
        assertEquals(rowColor(1, 2), rowColorFor(null, 1, 2))
        assertEquals(cellColor(1, 2, true), cellColorFor(null, 1, 2, true))
    }

    @Test
    fun `completed cell is lighter than pending cell`() {
        assertTrue(brightness(cellColorFor(rootColor, 0, 1, true)) > brightness(cellColorFor(rootColor, 0, 1, false)))
    }
}
