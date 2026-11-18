package com.example.taskflow.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

// dayOfWeek: 1 = lunes ... 7 = domingo. Devuelve siempre un instante posterior a nowMs.
fun nextWeeklyTrigger(nowMs: Long, dayOfWeek: Int, minute: Int, zone: ZoneId): Long {
    val target = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
    val m = minute.coerceIn(0, 1439)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    for (offset in 0..7) {
        val date = today.plusDays(offset.toLong())
        if (date.dayOfWeek != target) continue
        val ms = date.atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
        if (ms > nowMs) return ms
    }
    return nowMs + 7L * 24 * 60 * 60 * 1000
}
