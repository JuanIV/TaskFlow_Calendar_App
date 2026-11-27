package com.example.taskflow.domain

// Paleta ARGB pura para la rejilla de proyectos avanzados.
// Cada raiz tiene un color base. Cada nivel descendente se aclara con un paso fijo,
// con un limite minimo de luminosidad para que siempre sea legible.

private val ROOT_BASE_COLORS = intArrayOf(
    0xFF3949AB.toInt(), // indigo
    0xFF00897B.toInt(), // verde azulado
    0xFFF4511E.toInt(), // naranja oscuro
    0xFF8E24AA.toInt(), // morado
    0xFF1E88E5.toInt(), // azul
    0xFF43A047.toInt(), // verde
    0xFFD81B60.toInt(), // rosa
    0xFF00ACC1.toInt()  // cian
)

// Paso de aclarado por nivel, en fraccion de mezcla con blanco
private const val LIGHTEN_STEP = 0.18f

// Limite maximo de aclarado para que el texto siga siendo legible
private const val MAX_LIGHTEN = 0.72f

// Color base de una raiz, elegido por su posicion (indice en la lista de raices)
fun rootBaseColor(rootIndex: Int): Int =
    ROOT_BASE_COLORS[Math.floorMod(rootIndex, ROOT_BASE_COLORS.size)]

// Mezcla un color ARGB con blanco en la proporcion indicada (0 = sin cambio, 1 = blanco)
fun lightenArgb(color: Int, amount: Float): Int {
    val a = (color ushr 24) and 0xFF
    val r = (color ushr 16) and 0xFF
    val g = (color ushr 8) and 0xFF
    val b = color and 0xFF
    val t = amount.coerceIn(0f, 1f)
    fun mix(c: Int): Int = (c + (255 - c) * t).toInt().coerceIn(0, 255)
    return (a shl 24) or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
}

// Color de una fila segun su profundidad: cada nivel mas profundo se aclara un paso,
// sin superar MAX_LIGHTEN.
fun rowColor(rootIndex: Int, depth: Int): Int {
    val base = rootBaseColor(rootIndex)
    val amount = (depth * LIGHTEN_STEP).coerceAtMost(MAX_LIGHTEN)
    return lightenArgb(base, amount)
}

// Color de una celda pendiente (tono fuerte) y completada (tono claro) para una fila.
// Pendiente: el color de la fila. Completada: el mismo color mezclado con blanco al 65%.
fun cellColor(rootIndex: Int, depth: Int, completed: Boolean): Int {
    val row = rowColor(rootIndex, depth)
    return if (completed) lightenArgb(row, 0.65f) else row
}

// Color de una fila: el color elegido para su raiz (o el automatico por posicion), aclarado por profundidad
fun rowColorFor(rootColorArgb: Int?, rootIndex: Int, depth: Int): Int {
    val base = rootColorArgb ?: rootBaseColor(rootIndex)
    val amount = (depth * LIGHTEN_STEP).coerceAtMost(MAX_LIGHTEN)
    return lightenArgb(base, amount)
}

fun cellColorFor(rootColorArgb: Int?, rootIndex: Int, depth: Int, completed: Boolean): Int {
    val row = rowColorFor(rootColorArgb, rootIndex, depth)
    return if (completed) lightenArgb(row, 0.65f) else row
}
