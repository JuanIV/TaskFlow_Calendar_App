package com.example.taskflow.domain

// Tipos de proyecto. null en la base de datos equivale a SIMPLE (proyectos anteriores a la fase 9).
object ProjectKind {
    const val SIMPLE = "SIMPLE"
    const val ADVANCED = "ADVANCED"
}

// Valido si es null (sin tipo explicito), SIMPLE o ADVANCED
fun isValidProjectKind(kind: String?): Boolean =
    kind == null || kind == ProjectKind.SIMPLE || kind == ProjectKind.ADVANCED

// Tipo efectivo: cualquier valor distinto de ADVANCED se trata como SIMPLE
fun effectiveProjectKind(kind: String?): String =
    if (kind == ProjectKind.ADVANCED) ProjectKind.ADVANCED else ProjectKind.SIMPLE
