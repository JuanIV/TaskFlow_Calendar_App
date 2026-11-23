package com.example.taskflow.domain

import kotlin.math.roundToInt

// Dia nuevo al mover una barra. Nunca anterior a today.
fun dayAfterMove(startDay: Long, dxPx: Float, dayWidthPx: Float, today: Long): Long {
    val deltaDays = if (dayWidthPx <= 0f) 0 else (dxPx / dayWidthPx).roundToInt()
    return maxOf(today, startDay + deltaDays)
}

// Duracion en dias de una barra (inclusive, minimo 1)
fun barDays(startDay: Long, endDay: Long): Long = maxOf(1L, endDay - startDay + 1)

// Minutos nuevos al redimensionar una hoja. Escala la estimacion actual por la nueva duracion.
// Duracion minima: 60 minutos.
fun minutesAfterResize(
    currentMinutes: Int,
    oldDays: Long,
    dxPx: Float,
    dayWidthPx: Float
): Int {
    val deltaDays = if (dayWidthPx <= 0f) 0 else (dxPx / dayWidthPx).roundToInt()
    val newDays = oldDays + deltaDays
    val scaled = (currentMinutes.toLong() * newDays) / maxOf(1L, oldDays)
    return maxOf(60L, scaled).toInt()
}
