package com.example.taskflow.domain

import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventExceptionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventRecurrenceTest {

    private fun createBaseEvent(date: LocalDate, type: String = "NONE", interval: Int = 1): EventEntity {
        return EventEntity(
            id = 1, title = "Test Event", date = date.toEpochDay(), colorArgb = 0, createdAt = 0L,
            recurrenceType = type, recurrenceInterval = interval
        )
    }

    @Test
    fun `weekly multi day with interval 2 and exceptions`() {
        // Inicia el Lunes 2 de Noviembre 2026. Intervalo de 2 semanas, Lunes y Miercoles.
        // 1 = Lunes, 4 = Miercoles -> Mask = 1 (L) + 4 (X) = 5
        val startDate = LocalDate.of(2026, 11, 2)
        val event = createBaseEvent(startDate, "WEEKLY", 2).copy(
            endDate = startDate.toEpochDay() + 1, // Dura 2 dias
            recurrenceWeekdaysMask = 5
        )

        // Excepcion: Borramos el primer Miercoles (Nov 4)
        val ex1 = EventExceptionEntity(
            eventId = 1, originalDate = LocalDate.of(2026, 11, 4).toEpochDay(), isDeleted = true,
            overrideTitle = "", overrideDate = 0L, overrideEndDate = null, overrideAllDay = true, overrideStartMinute = null, overrideEndMinute = null, overrideColorArgb = 0, overrideNotes = null, overrideLocation = null
        )

        // Expandir todo Noviembre
        val from = LocalDate.of(2026, 11, 1).toEpochDay()
        val to = LocalDate.of(2026, 11, 30).toEpochDay()

        val occurrences = expandEvents(listOf(event), listOf(ex1), from, to)

        // Deberian ser:
        // - Lunes 2 (Aplica, dura hasta el 3)
        // - Miercoles 4 (Borrado por excepcion)
        // - Lunes 9 y Miercoles 11 (Saltados por intervalo 2)
        // - Lunes 16 (Aplica)
        // - Miercoles 18 (Aplica)
        // - Lunes 30 (Aplica)

        assertEquals(4, occurrences.size)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), occurrences[0].startDate)
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(), occurrences[1].startDate)
        assertEquals(LocalDate.of(2026, 11, 18).toEpochDay(), occurrences[2].startDate)
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), occurrences[3].startDate)

        // Verificamos duracion heredada del padre
        assertEquals(LocalDate.of(2026, 11, 3).toEpochDay(), occurrences[0].endDate)
    }

    @Test
    fun `monthly 31st resolves to last day of shorter months`() {
        val startDate = LocalDate.of(2026, 1, 31) // Enero 31
        val event = createBaseEvent(startDate, "MONTHLY_DAY", 1).copy(recurrenceEndType = "COUNT", recurrenceCount = 3) // Enero, Febrero, Marzo

        val occurrences = expandEvents(listOf(event), emptyList(), startDate.toEpochDay(), startDate.plusDays(100).toEpochDay())

        assertEquals(3, occurrences.size)
        assertEquals(LocalDate.of(2026, 1, 31).toEpochDay(), occurrences[0].startDate)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), occurrences[1].startDate) // Febrero solo tiene 28
        assertEquals(LocalDate.of(2026, 3, 31).toEpochDay(), occurrences[2].startDate)
    }

    @Test
    fun `yearly leap year 29th feb resolves to 28th`() {
        val startDate = LocalDate.of(2024, 2, 29) // Bisiesto
        val event = createBaseEvent(startDate, "YEARLY", 1).copy(recurrenceEndType = "COUNT", recurrenceCount = 2)

        val occurrences = expandEvents(listOf(event), emptyList(), 0, Long.MAX_VALUE)

        assertEquals(2, occurrences.size)
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), occurrences[0].startDate)
        assertEquals(LocalDate.of(2025, 2, 28).toEpochDay(), occurrences[1].startDate) // 2025 no bisiesto
    }

    @Test
    fun `monthly nth weekday`() {
        // Nov 10, 2026 es el segundo Martes del mes
        val startDate = LocalDate.of(2026, 11, 10)
        val event = createBaseEvent(startDate, "MONTHLY_NTH_WEEKDAY", 1).copy(recurrenceEndType = "COUNT", recurrenceCount = 2)

        val occurrences = expandEvents(listOf(event), emptyList(), 0, Long.MAX_VALUE)

        assertEquals(2, occurrences.size)
        assertEquals(LocalDate.of(2026, 11, 10).toEpochDay(), occurrences[0].startDate)
        // Siguiente mes (Diciembre 2026), el segundo Martes es el 8 de Diciembre
        assertEquals(LocalDate.of(2026, 12, 8).toEpochDay(), occurrences[1].startDate)
    }
}
