package com.example.taskflow.ui.planner

// Devuelve el indice de columna (0 = sin fecha, 1..7 = dias) que contiene la coordenada X del contenido, o null.
fun plannerColumnIndex(contentX: Float, padPx: Float, colPx: Float, gapPx: Float, columns: Int): Int? {
    val x = contentX - padPx
    if (x < 0f) return null
    val idx = (x / (colPx + gapPx)).toInt()
    return if (idx in 0 until columns) idx else null
}
