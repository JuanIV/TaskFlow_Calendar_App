package com.example.taskflow.domain

import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskCompletionEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.data.entities.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLogicTest {

    @Test
    fun `getTodayTasksForNotification filters overdue based on mode`() {
        val today = 100L
        val tasks = listOf(
            TaskEntity(1, "Hoy", date = today, createdAt = 0L),
            TaskEntity(2, "Atrasada", date = today - 1, createdAt = 0L),
            TaskEntity(3, "Futura", date = today + 1, createdAt = 0L)
        )

        val modeShow = getTodayTasksForNotification(tasks, today, "SHOW")
        assertEquals(2, modeShow.size) // Hoy + Atrasada

        val modeMove = getTodayTasksForNotification(tasks, today, "MOVE_TO_TODAY")
        assertEquals(1, modeMove.size) // Solo hoy (las atrasadas ya habrian sido movidas por el scheduler)
    }

    @Test
    fun `routine visibility respects window and optional tasks`() {
        val today = 100L
        val routine = RoutineEntity(1, "Test", 0, "EVERY_N_DAYS", 0, 1, 0L, startMinuteOfDay = 600, endMinuteOfDay = 1200, isActive = true, createdAt = 0L)

        val tasks = listOf(
            RoutineTaskEntity(1, 1, "Obligatoria", 0, isOptional = false),
            RoutineTaskEntity(2, 1, "Opcional", 1, isOptional = true)
        )

        // Faltan tareas y estamos en ventana (600 a 1200, dia inicia 0) -> Visible
        assertTrue(isRoutineNotificationVisible(routine, today, 700, 0, tasks, emptyList()))

        // Completamos solo la obligatoria -> NO Visible (opcionales no bloquean el cierre)
        val completions = listOf(RoutineTaskCompletionEntity(1, today, 0L))
        assertFalse(isRoutineNotificationVisible(routine, today, 700, 0, tasks, completions))

        // Fuera de ventana (antes de las 600) -> NO Visible
        assertFalse(isRoutineNotificationVisible(routine, today, 500, 0, tasks, emptyList()))
    }
}
