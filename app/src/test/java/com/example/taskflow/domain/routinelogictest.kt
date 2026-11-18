package com.example.taskflow.domain

import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoutineLogicTest {

    private fun baseRoutine() = RoutineEntity(
        id = 1, name = "Test", colorArgb = 0, recurrenceType = "EVERY_N_DAYS",
        weekdaysMask = 0, intervalDays = 1, anchorDate = 0L, startMinuteOfDay = 0,
        isActive = true, createdAt = 0L
    )

    @Test
    fun `routine does not apply if template or inactive`() {
        val date = 10000L
        assertFalse(routineAppliesOn(baseRoutine().copy(isTemplate = true), date))
        assertFalse(routineAppliesOn(baseRoutine().copy(isActive = false), date))
    }

    @Test
    fun `routine pauses correctly`() {
        val date = LocalDate.of(2026, 11, 15).toEpochDay()
        val routine = baseRoutine().copy(
            pausedFromEpochDay = date - 1,
            pausedUntilEpochDay = date + 1
        )
        // Dentro de pausa (día 15 cae entre el 14 y el 16)
        assertFalse(routineAppliesOn(routine, date))
        // Fuera de pausa
        assertTrue(routineAppliesOn(routine, date + 2))
    }

    @Test
    fun `task applies on specific days`() {
        // 2026-11-16 es Lunes (bit 1 = 1)
        val mondayEpoch = LocalDate.of(2026, 11, 16).toEpochDay()
        val task = RoutineTaskEntity(routineId = 1, title = "T", sortOrder = 0)

        // Si no tiene máscara, aplica siempre
        assertTrue(taskAppliesOn(task.copy(weekdaysMask = null), mondayEpoch))

        // Si tiene máscara Lunes (1), aplica
        assertTrue(taskAppliesOn(task.copy(weekdaysMask = 1), mondayEpoch))

        // Si tiene máscara Martes (2), no aplica el lunes
        assertFalse(taskAppliesOn(task.copy(weekdaysMask = 2), mondayEpoch))
    }
}
