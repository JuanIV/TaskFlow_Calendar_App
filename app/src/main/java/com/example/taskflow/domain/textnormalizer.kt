package com.example.taskflow.domain

import java.text.Normalizer

// Minusculas y sin tildes: "Reunion" y "reunion" dan el mismo resultado
fun normalizeText(text: String): String {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
}
