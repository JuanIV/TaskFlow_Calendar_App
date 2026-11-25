package com.example.taskflow.domain

// Modo de seguimiento de un proyecto. Es ortogonal al tipo (ProjectKind).
// DYNAMIC: se materializan las tareas segun el reparto (partes o celdas).
// DAILY: se materializa un recordatorio generico "Avanzar en X".
// NONE: cada hoja pendiente es una tarea suelta sin fecha.
object TrackingMode {
    const val DYNAMIC = "DYNAMIC"
    const val DAILY = "DAILY"
    const val NONE = "NONE"
}

fun isValidTrackingMode(mode: String?): Boolean =
    mode == TrackingMode.DYNAMIC || mode == TrackingMode.DAILY || mode == TrackingMode.NONE

// Cualquier valor desconocido se trata como DYNAMIC
fun effectiveTrackingMode(mode: String?): String = when (mode) {
    TrackingMode.DAILY -> TrackingMode.DAILY
    TrackingMode.NONE -> TrackingMode.NONE
    else -> TrackingMode.DYNAMIC
}
