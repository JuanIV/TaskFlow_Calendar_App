package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProjectIntegrationTest {

    private val mon = LocalDate.of(2026, 11, 23).toEpochDay()

    @Test
    fun `single part label has no part suffix`() {
        val part = PlanPart(1L, 1, 1, mon, 60)
        assertEquals("Web: Dise\u00f1ar", projectTodayLabel("Web", part, "Dise\u00f1ar").replace("\u00f1", "\u00f1"))
    }

    @Test
    fun `split task label shows part index`() {
        val part = PlanPart(1L, 1, 2, mon, 240)
        assertEquals("Web: Maquetar (parte 1/2)", projectTodayLabel("Web", part, "Maquetar"))
    }

    @Test
    fun `today items come only from today parts`() {
        val plan = planProject(
            PlanConfig(mon, null, 240, 31, emptySet(), false),
            listOf(
                PlanNode(1L, null, 1, false, 360, 0, false, null, null),
                PlanNode(2L, null, 2, false, 60, 0, false, null, null)
            ),
            mon
        )
        val items = projectTodayItems(7L, "Web", plan, mapOf(1L to "A", 2L to "B"), mon)
        assertEquals(listOf("Web: A (parte 1/2)"), items.map { it.label })
        assertEquals(240, items.first().minutes)
    }

    @Test
    fun `first alert when project enters margin one day`() {
        assertEquals(RiskAlert.AT_RISK, riskAlertFor(PlanStatus.OK, PlanStatus.JUSTO))
        assertEquals(RiskAlert.AT_RISK, riskAlertFor(null, PlanStatus.JUSTO))
    }

    @Test
    fun `alert when project becomes late`() {
        assertEquals(RiskAlert.NO_MARGIN, riskAlertFor(PlanStatus.JUSTO, PlanStatus.ATRASADO))
        assertEquals(RiskAlert.NO_MARGIN, riskAlertFor(PlanStatus.OK, PlanStatus.ATRASADO))
    }

    @Test
    fun `no repeated alert while the status stays the same`() {
        assertEquals(RiskAlert.NONE, riskAlertFor(PlanStatus.JUSTO, PlanStatus.JUSTO))
        assertEquals(RiskAlert.NONE, riskAlertFor(PlanStatus.ATRASADO, PlanStatus.ATRASADO))
    }

    @Test
    fun `no alert when status improves or is ok`() {
        assertEquals(RiskAlert.NONE, riskAlertFor(PlanStatus.ATRASADO, PlanStatus.OK))
        assertEquals(RiskAlert.NONE, riskAlertFor(PlanStatus.JUSTO, PlanStatus.OK))
        assertEquals(RiskAlert.NONE, riskAlertFor(PlanStatus.OK, PlanStatus.OK))
    }
}
