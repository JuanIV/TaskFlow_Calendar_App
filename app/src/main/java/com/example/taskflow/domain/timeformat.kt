package com.example.taskflow.domain

import java.util.Locale

// Formato de hora de toda la app. Se actualiza al arrancar y al cambiar el ajuste.
object TimeFormatState {
    @Volatile
    var use24h: Boolean = true
}

fun formatMinute(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    val hour = m / 60
    val min = m % 60
    return if (TimeFormatState.use24h) {
        String.format(Locale.ROOT, "%02d:%02d", hour, min)
    } else {
        val suffix = if (hour < 12) "AM" else "PM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        String.format(Locale.ROOT, "%d:%02d %s", hour12, min, suffix)
    }
}
