package com.example.taskflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IcsCodecTest {

    private fun day(d: Int) = LocalDate.of(2026, 11, d).toEpochDay()

    @Test
    fun `parses a timed event with alarm and escapes`() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Reunion\, equipo
            DTSTART:20261118T093000
            DTEND:20261118T103000
            LOCATION:Madrid
            BEGIN:VALARM
            TRIGGER:-PT15M
            ACTION:DISPLAY
            DESCRIPTION:Aviso
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val list = parseIcs(text)
        assertEquals(1, list.size)
        val e = list[0]
        assertEquals("Reunion, equipo", e.title)
        assertEquals(day(18), e.startDay)
        assertFalse(e.allDay)
        assertEquals(570, e.startMinute)
        assertEquals(630, e.endMinute)
        assertNull(e.endDay)
        assertNull(e.notes)
        assertEquals("Madrid", e.location)
        assertEquals(listOf(15), e.reminders)
    }

    @Test
    fun `parses a multi day all day event`() {
        val text = """
            BEGIN:VEVENT
            SUMMARY:Viaje
            DTSTART;VALUE=DATE:20261120
            DTEND;VALUE=DATE:20261123
            END:VEVENT
        """.trimIndent()
        val e = parseIcs(text)[0]
        assertTrue(e.allDay)
        assertNull(e.startMinute)
        assertEquals(day(20), e.startDay)
        assertEquals(day(22), e.endDay)
    }

    @Test
    fun `parses a weekly rule`() {
        val text = """
            BEGIN:VEVENT
            SUMMARY:Clase
            DTSTART:20261102T080000
            RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=5
            END:VEVENT
        """.trimIndent()
        val e = parseIcs(text)[0]
        assertEquals("WEEKLY", e.recurrenceType)
        assertEquals(2, e.recurrenceInterval)
        assertEquals(5, e.recurrenceMask)
        assertEquals("COUNT", e.recurrenceEndType)
        assertEquals(5, e.recurrenceCount)
    }

    @Test
    fun `export then import keeps the data`() {
        val original = IcsEvent(
            title = "Gimnasio, tarde",
            startDay = day(2),
            allDay = false,
            startMinute = 1080,
            endDay = null,
            endMinute = 1140,
            notes = "Linea1\nLinea2",
            location = "Calle; 5",
            recurrenceType = "WEEKLY",
            recurrenceInterval = 2,
            recurrenceMask = 5,
            recurrenceEndType = "COUNT",
            recurrenceUntil = null,
            recurrenceCount = 6,
            reminders = listOf(30)
        )
        val ics = buildIcs(listOf(original))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;INTERVAL=2;COUNT=6"))
        val back = parseIcs(ics)[0]
        assertEquals(original.title, back.title)
        assertEquals(original.startDay, back.startDay)
        assertEquals(original.startMinute, back.startMinute)
        assertEquals(original.endMinute, back.endMinute)
        assertEquals(original.notes, back.notes)
        assertEquals(original.location, back.location)
        assertEquals("WEEKLY", back.recurrenceType)
        assertEquals(2, back.recurrenceInterval)
        assertEquals(5, back.recurrenceMask)
        assertEquals("COUNT", back.recurrenceEndType)
        assertEquals(6, back.recurrenceCount)
        assertEquals(listOf(30), back.reminders)
    }

    @Test
    fun `csv fields are escaped`() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a;b\"", csvField("a;b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("", csvField(null))
    }

    @Test
    fun `csv has bom header and rows`() {
        val csv = buildCsv(listOf("a", "b"), listOf(listOf("1", "2;3")))
        assertEquals("\uFEFFa;b\r\n1;\"2;3\"\r\n", csv)
    }
}
