package com.example.taskflow.ui.calendar

import com.example.taskflow.domain.EventOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

const val PAGER_CENTER = 3000
const val PAGER_COUNT = 6000

fun EventOccurrence.isMultiDay(): Boolean {
    val end = endDate
    return end != null && end > startDate
}

// Va en la franja superior: todo el dia, sin hora o de varios dias
fun EventOccurrence.isStripEvent(): Boolean = allDay || startMinute == null || isMultiDay()

fun sortOccurrences(list: List<EventOccurrence>): List<EventOccurrence> =
    list.sortedWith(compareBy({ !it.allDay }, { it.startMinute ?: 0 }, { it.title }))

// Reparte cada ocurrencia en todos los dias que abarca
fun groupByDay(occurrences: List<EventOccurrence>): Map<Long, List<EventOccurrence>> {
    val map = HashMap<Long, MutableList<EventOccurrence>>()
    for (occ in occurrences) {
        val last = occ.endDate ?: occ.startDate
        var day = occ.startDate
        while (day <= last && day - occ.startDate <= 366) {
            map.getOrPut(day) { mutableListOf() }.add(occ)
            day++
        }
    }
    return map.mapValues { sortOccurrences(it.value) }
}

fun weekStartOf(date: LocalDate, first: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(first))

fun mondayOf(date: LocalDate): LocalDate = weekStartOf(date, DayOfWeek.MONDAY)

fun pageForDate(view: String, today: LocalDate, date: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): Int = when (view) {
    "MONTH" -> PAGER_CENTER + ChronoUnit.MONTHS.between(today.withDayOfMonth(1), date.withDayOfMonth(1)).toInt()
    "WEEK" -> PAGER_CENTER + ChronoUnit.WEEKS.between(weekStartOf(today, first), weekStartOf(date, first)).toInt()
    else -> PAGER_CENTER + ChronoUnit.DAYS.between(today, date).toInt()
}

fun dateForPage(view: String, today: LocalDate, page: Int, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
    val offset = (page - PAGER_CENTER).toLong()
    return when (view) {
        "MONTH" -> today.withDayOfMonth(1).plusMonths(offset)
        "WEEK" -> weekStartOf(today, first).plusWeeks(offset)
        else -> today.plusDays(offset)
    }
}

// Fecha "ancla" de una pagina: hoy si la pagina lo contiene
fun anchorForPage(view: String, today: LocalDate, page: Int, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
    val start = dateForPage(view, today, page, first)
    return when (view) {
        "MONTH" -> if (start.year == today.year && start.month == today.month) today else start
        "WEEK" -> if (!today.isBefore(start) && !today.isAfter(start.plusDays(6))) today else start
        else -> start
    }
}
