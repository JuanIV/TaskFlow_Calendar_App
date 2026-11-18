package com.example.taskflow.ui.calendar

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.taskflow.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// Primer dia de la semana elegido en ajustes
val LocalFirstDay = compositionLocalOf { DayOfWeek.MONDAY }

fun getTextColorForBackground(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White
}

fun getDaysInMonthGrid(yearMonth: YearMonth, first: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
    val firstOfMonth = yearMonth.atDay(1)
    val offset = (firstOfMonth.dayOfWeek.value - first.value + 7) % 7
    val start = firstOfMonth.minusDays(offset.toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

private val WEEKDAY_RES = listOf(
    R.string.s2_wd_1, R.string.s2_wd_2, R.string.s2_wd_3, R.string.s2_wd_4,
    R.string.s2_wd_5, R.string.s2_wd_6, R.string.s2_wd_7
)

// Cabeceras L M X J V S D rotadas segun el primer dia de la semana
fun weekdayHeaderRes(first: DayOfWeek): List<Int> =
    (0 until 7).map { WEEKDAY_RES[(first.value - 1 + it) % 7] }
