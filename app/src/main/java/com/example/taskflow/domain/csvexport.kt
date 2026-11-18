package com.example.taskflow.domain

fun csvField(value: String?): String {
    val v = value ?: ""
    val needsQuotes = v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuotes) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

// Separador ";" y BOM UTF-8 para que Excel en español lo abra bien
fun buildCsv(header: List<String>, rows: List<List<String?>>): String {
    val sb = StringBuilder("\uFEFF")
    sb.append(header.joinToString(";") { csvField(it) }).append("\r\n")
    for (row in rows) {
        sb.append(row.joinToString(";") { csvField(it) }).append("\r\n")
    }
    return sb.toString()
}
