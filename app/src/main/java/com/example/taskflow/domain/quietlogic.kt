package com.example.taskflow.domain

// Evalúa si un minuto concreto cae dentro del horario de silencio,
// manejando correctamente cruces de la medianoche lógica.
fun isQuietNow(currentMinuteOfDay: Int, startQuiet: Int, endQuiet: Int, dayStartHour: Int): Boolean {
    val offNow = getLogicalMinuteOffset(currentMinuteOfDay, dayStartHour)
    val offStart = getLogicalMinuteOffset(startQuiet, dayStartHour)
    val offEnd = getLogicalMinuteOffset(endQuiet, dayStartHour)

    return if (offStart < offEnd) {
        // Rango normal en el mismo día lógico
        offNow in offStart until offEnd
    } else if (offStart > offEnd) {
        // Cruza la medianoche lógica (ej: 23:00 a 07:00 con dayStart = 00:00)
        offNow >= offStart || offNow < offEnd
    } else {
        // start == end, no hay silencio
        false
    }
}
