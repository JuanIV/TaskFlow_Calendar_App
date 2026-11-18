package com.example.taskflow.domain

import java.time.ZoneId
import java.time.ZonedDateTime

fun logicalDateNow(dayStartHour: Int): Long {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val adjusted = if (now.hour < dayStartHour) {
        now.minusDays(1)
    } else {
        now
    }
    return adjusted.toLocalDate().toEpochDay()
}

// Calcula el desplazamiento en minutos respecto al inicio del dia logico
fun getLogicalMinuteOffset(minuteOfDay: Int, dayStartHour: Int): Int {
    val dayStartMinute = dayStartHour * 60
    return (minuteOfDay - dayStartMinute + 1440) % 1440
}

// Comprueba si una ventana de tiempo [inicio, fin) es valida en el dia logico
fun isValidLogicalWindow(startMinute: Int, endMinute: Int, dayStartHour: Int): Boolean {
    val offStart = getLogicalMinuteOffset(startMinute, dayStartHour)
    val offEnd = getLogicalMinuteOffset(endMinute, dayStartHour)
    return offEnd > offStart
}

// Comprueba si un minuto actual cae dentro de la ventana logica
fun isMinuteInLogicalWindow(currentMinute: Int, startMinute: Int, endMinute: Int, dayStartHour: Int): Boolean {
    val offNow = getLogicalMinuteOffset(currentMinute, dayStartHour)
    val offStart = getLogicalMinuteOffset(startMinute, dayStartHour)
    val offEnd = getLogicalMinuteOffset(endMinute, dayStartHour)
    return offNow >= offStart && offNow < offEnd
}
