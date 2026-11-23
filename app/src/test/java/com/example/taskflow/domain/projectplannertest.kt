package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectPlannerTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()
    private val tue = mon + 1
    private val wed = mon + 2
    private val fri = mon + 4
    private val nextMon = mon + 7
    private val weekdays = 31

    private fun cfg(
        start: Long = mon,
        capacity: Int = 240,
        mask: Int = weekdays,
        deadline: Long? = null,
        excluded: Set<Long> = emptySet(),
        paused: Boolean = false
    ) = PlanConfig(start, deadline, capacity, mask, excluded, paused)

    private fun leaf(
        id: Long,
        minutes: Int,
        parent: Long? = null,
        order: Int = id.toInt(),
        done: Int = 0,
        completed: Boolean = false,
        notBefore: Long? = null,
        pulled: Long? = null
    ) = PlanNode(id, parent, order, false, minutes, done, completed, notBefore, pulled)

    private fun milestone(id: Long, parent: Long? = null, order: Int = id.toInt()) =
        PlanNode(id, parent, order, true, 0, 0, false, null, null)

    private fun group(id: Long, parent: Long? = null, order: Int = id.toInt()) =
        PlanNode(id, parent, order, false, 0, 0, false, null, null)

    private fun partsOf(plan: ProjectPlan, id: Long) = plan.parts.filter { it.nodeId == id }

    @Test
    fun `leaf of one and a half days is split in two parts`() {
        val plan = planProject(cfg(), listOf(leaf(1, 360)), mon)
        assertEquals(listOf(PlanPart(1, 1, 2, mon, 240), PlanPart(1, 2, 2, tue, 120)), plan.parts)
    }

    @Test
    fun `short tasks share the same day until capacity is full`() {
        val nodes = listOf(leaf(2, 60), leaf(3, 60), leaf(4, 120), leaf(5, 60))
        val plan = planProject(cfg(), nodes, mon)
        assertEquals(
            listOf(
                PlanPart(2, 1, 1, mon, 60),
                PlanPart(3, 1, 1, mon, 60),
                PlanPart(4, 1, 1, mon, 120),
                PlanPart(5, 1, 1, tue, 60)
            ),
            plan.parts
        )
    }

    @Test
    fun `weekend days are skipped`() {
        val plan = planProject(cfg(start = fri), listOf(leaf(1, 480)), fri)
        assertEquals(listOf(PlanPart(1, 1, 2, fri, 240), PlanPart(1, 2, 2, nextMon, 240)), plan.parts)
    }

    @Test
    fun `excluded days are skipped`() {
        val plan = planProject(cfg(excluded = setOf(tue)), listOf(leaf(1, 480)), mon)
        assertEquals(listOf(PlanPart(1, 1, 2, mon, 240), PlanPart(1, 2, 2, wed, 240)), plan.parts)
    }

    @Test
    fun `partial progress keeps only the remaining minutes`() {
        val plan = planProject(cfg(), listOf(leaf(1, 480, done = 240)), mon)
        assertEquals(listOf(PlanPart(1, 1, 1, mon, 240)), plan.parts)
        assertEquals(240, plan.remainingMinutes)
    }

    @Test
    fun `completed leaf has no parts and no dates`() {
        val plan = planProject(cfg(), listOf(leaf(1, 60, completed = true)), mon)
        assertTrue(plan.parts.isEmpty())
        assertNull(plan.nodes.getValue(1).startDay)
        assertTrue(plan.nodes.getValue(1).completed)
        assertEquals(PlanStatus.SIN_PENDIENTE, plan.status)
    }

    @Test
    fun `group dates and progress come from its children`() {
        val nodes = listOf(group(10), leaf(11, 60, parent = 10, completed = true), leaf(12, 120, parent = 10))
        val plan = planProject(cfg(), nodes, mon)
        val g = plan.nodes.getValue(10)
        assertEquals(mon, g.startDay)
        assertEquals(mon, g.endDay)
        assertFalse(g.completed)
        assertEquals(33, g.progressPercent)
    }

    @Test
    fun `group completes when all children complete`() {
        val nodes = listOf(group(10), leaf(11, 60, parent = 10, completed = true), leaf(12, 60, parent = 10, done = 60))
        val plan = planProject(cfg(), nodes, mon)
        assertTrue(plan.nodes.getValue(10).completed)
        assertEquals(100, plan.nodes.getValue(10).progressPercent)
    }

    @Test
    fun `milestone is dated on the day the previous work ends`() {
        val plan = planProject(cfg(), listOf(leaf(1, 120), milestone(2)), mon)
        assertEquals(mon, plan.nodes.getValue(2).startDay)
        assertEquals(mon, plan.nodes.getValue(2).endDay)
        assertTrue(partsOf(plan, 2).isEmpty())
    }

    @Test
    fun `not before date delays the start`() {
        val plan = planProject(cfg(), listOf(leaf(1, 60, notBefore = wed)), mon)
        assertEquals(listOf(PlanPart(1, 1, 1, wed, 60)), plan.parts)
    }

    @Test
    fun `plan is recalculated from today and never restores past days`() {
        val plan = planProject(cfg(start = mon), listOf(leaf(1, 480)), tue)
        assertEquals(listOf(PlanPart(1, 1, 2, tue, 240), PlanPart(1, 2, 2, wed, 240)), plan.parts)
    }

    @Test
    fun `deadline status is OK when margin is two days or more`() {
        // Fin previsto: martes. Deadline: jueves. Margen: 2
        val plan = planProject(cfg(deadline = wed + 1), listOf(leaf(1, 480)), mon)
        assertEquals(PlanStatus.OK, plan.status)
        assertEquals(2, plan.marginDays)
    }

    @Test
    fun `deadline status is JUSTO when margin is one day`() {
        val plan = planProject(cfg(deadline = wed), listOf(leaf(1, 480)), mon)
        assertEquals(PlanStatus.JUSTO, plan.status)
        assertEquals(1, plan.marginDays)
    }

    @Test
    fun `deadline status is ATRASADO with overdue days when margin is negative`() {
        val plan = planProject(cfg(deadline = mon - 1), listOf(leaf(1, 480)), mon)
        assertEquals(PlanStatus.ATRASADO, plan.status)
        assertEquals(-2, plan.marginDays)
        assertEquals(2, plan.overdueDays)
    }

    @Test
    fun `no deadline gives status SIN_DEADLINE`() {
        val plan = planProject(cfg(), listOf(leaf(1, 480)), mon)
        assertEquals(PlanStatus.SIN_DEADLINE, plan.status)
        assertNull(plan.marginDays)
    }

    @Test
    fun `zero capacity gives no parts and status SIN_CAPACIDAD`() {
        val plan = planProject(cfg(capacity = 0), listOf(leaf(1, 60)), mon)
        assertTrue(plan.parts.isEmpty())
        assertEquals(PlanStatus.SIN_CAPACIDAD, plan.status)
    }

    @Test
    fun `paused project has no parts and status PAUSADO`() {
        val plan = planProject(cfg(paused = true), listOf(leaf(1, 60)), mon)
        assertTrue(plan.parts.isEmpty())
        assertEquals(PlanStatus.PAUSADO, plan.status)
    }

    @Test
    fun `applyPartDone accumulates and completes at estimate, reopen resets`() {
        val n1 = applyPartDone(leaf(1, 480), 240)
        assertEquals(240, n1.doneMinutes)
        assertFalse(n1.completed)
        val n2 = applyPartDone(n1, 240)
        assertEquals(480, n2.doneMinutes)
        assertTrue(n2.completed)
        val n3 = reopenNode(n2)
        assertEquals(0, n3.doneMinutes)
        assertFalse(n3.completed)
    }

    @Test
    fun `pull forward fills spare time today and ignores not before`() {
        val nodes = listOf(leaf(1, 60), leaf(2, 120, notBefore = wed), leaf(3, 240))
        val config = cfg()
        assertEquals(2L, findPullCandidate(config, nodes, mon))

        val pulledNodes = nodes.map { if (it.id == 2L) it.copy(pulledForwardDay = mon) else it }
        val plan = planProject(config, pulledNodes, mon)
        assertEquals(listOf(PlanPart(2, 1, 1, mon, 120)), partsOf(plan, 2))
        assertEquals(PlanPart(3, 1, 2, mon, 60), partsOf(plan, 3).first())
        assertEquals(PlanPart(3, 2, 2, tue, 180), partsOf(plan, 3).last())
    }

    @Test
    fun `five hundred tasks are planned quickly and correctly`() {
        val nodes = (1L..500L).map { leaf(it, 60) }
        val start = System.nanoTime()
        val plan = planProject(cfg(), nodes, mon)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // 500 x 60 min = 30000 min = 125 dias laborables de 240 min
        assertEquals(125, plan.parts.map { it.day }.distinct().size)
        assertEquals(mon + 172, plan.expectedEndDay)
        assertTrue("Plan demasiado lento: $elapsedMs ms", elapsedMs < 500)
    }
}
