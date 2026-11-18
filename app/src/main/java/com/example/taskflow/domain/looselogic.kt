package com.example.taskflow.domain

import java.time.Instant
import java.time.ZoneId

// Siguiente instante del recordatorio periodico de tareas sueltas.
// Candidatos: inicio de ventana + k * intervalo, mientras sean <= fin de ventana.
fun nextLooseReminderTrigger(
    nowMs: Long,
    windowStartMin: Int,
    windowEndMin: Int,
    intervalHours: Int,
    zone: ZoneId
): Long {
    val interval = intervalHours.coerceAtLeast(1) * 60
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    for (dayOffset in 0..2) {
        val date = today.plusDays(dayOffset.toLong())
        var minute = windowStartMin.coerceIn(0, 1439)
        val end = windowEndMin.coerceIn(0, 1439)
        while (minute <= end) {
            val ms = date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
            if (ms > nowMs) return ms
            minute += interval
        }
    }
    return nowMs + interval * 60_000L
}
