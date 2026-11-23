package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectDependencyTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()
    private val wed = mon + 2
    private val cfg = PlanConfig(mon, null, 240, 31, emptySet(), false)

    private fun leaf(
        id: Long,
        minutes: Int,
        order: Int,
        parent: Long? = null,
        preds: List<Long> = emptyList(),
        notBefore: Long? = null
    ) = PlanNode(id, parent, order, false, minutes, 0, false, notBefore, null, preds)

    private fun group(id: Long, order: Int) =
        PlanNode(id, null, order, false, 0, 0, false, null, null, emptyList())

    @Test
    fun `dependency runs the predecessor first even if listed later`() {
        val nodes = listOf(leaf(1, 60, 1, preds = listOf(2)), leaf(2, 60, 2))
        val plan = planProject(cfg, nodes, mon)
        assertEquals(listOf(2L, 1L), plan.executionOrder)
        assertEquals(PlanStatus.SIN_DEADLINE, plan.status)
    }

    @Test
    fun `cycle gives status CICLO and no parts`() {
        val nodes = listOf(leaf(1, 60, 1, preds = listOf(2)), leaf(2, 60, 2, preds = listOf(1)))
        val plan = planProject(cfg, nodes, mon)
        assertEquals(PlanStatus.CICLO, plan.status)
        assertTrue(plan.parts.isEmpty())
        assertNull(orderWork(nodes))
    }

    @Test
    fun `group as predecessor means all its leaves go first`() {
        val nodes = listOf(
            leaf(20, 60, 1, preds = listOf(10)),
            group(10, 2),
            leaf(11, 60, 1, parent = 10)
        )
        val plan = planProject(cfg, nodes, mon)
        assertEquals(listOf(11L, 20L), plan.executionOrder)
    }

    @Test
    fun `without dependencies the order is the list order`() {
        val nodes = listOf(leaf(1, 60, 1), leaf(2, 60, 2), leaf(3, 60, 3))
        val plan = planProject(cfg, nodes, mon)
        assertEquals(listOf(1L, 2L, 3L), plan.executionOrder)
    }

    @Test
    fun `chain without gaps is fully critical`() {
        val nodes = listOf(leaf(1, 60, 1), leaf(2, 60, 2, preds = listOf(1)))
        val plan = planProject(cfg, nodes, mon)
        assertEquals(setOf(1L, 2L), plan.criticalNodeIds)
    }

    @Test
    fun `critical chain starts at a not before restriction`() {
        val nodes = listOf(leaf(1, 60, 1), leaf(3, 60, 3, notBefore = wed))
        val plan = planProject(cfg, nodes, mon)
        assertEquals(setOf(3L), plan.criticalNodeIds)
    }

    @Test
    fun `orderWork returns the same order as preorder when there are no dependencies`() {
        val nodes = listOf(leaf(1, 60, 1), leaf(2, 60, 2))
        assertEquals(listOf(1L, 2L), orderWork(nodes)!!.order.map { it.id })
    }
}
