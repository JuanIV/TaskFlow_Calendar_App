package com.example.taskflow.domain

import java.time.LocalDate
import java.time.ZoneId

// Devuelve el instante (milisegundos) para un minuto especifico de un dia logico
fun getLogicalInstant(epochDay: Long, dayStartHour: Int, minuteOfDay: Int): Long {
    val offset = (minuteOfDay - (dayStartHour * 60) + 1440) % 1440
    val totalMinutesFromStart = (dayStartHour * 60) + offset
    val hours = totalMinutesFromStart / 60
    val minutes = totalMinutesFromStart % 60

    // Si cruza la medianoche (hours >= 24), se suma un dia de pared
    val actualDate = if (hours >= 24) {
        LocalDate.ofEpochDay(epochDay).plusDays(1)
    } else {
        LocalDate.ofEpochDay(epochDay)
    }

    return actualDate.atTime(hours % 24, minutes)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
