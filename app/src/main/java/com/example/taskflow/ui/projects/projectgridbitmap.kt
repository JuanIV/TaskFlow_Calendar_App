package com.example.taskflow.ui.projects

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.example.taskflow.domain.GridDays
import com.example.taskflow.domain.GridRow
import com.example.taskflow.domain.cellColor
import com.example.taskflow.domain.weekCellsOf
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Dimensiones del PNG de la rejilla, en pixeles
private const val ROW_H = 36
private const val HEADER_H = 40
private const val TITLE_W = 280
private const val DAY_W = 26
private const val WEEK_W = 48
private const val MARGIN = 10

// Dibuja un PNG de la rejilla completa: columna de titulos con indentacion y color por jerarquia,
// mas la cuadricula de celdas en modo dia o semana, segun el modo activo en pantalla.
fun renderGridPng(
    context: Context,
    fileName: String,
    rows: List<GridRow>,
    days: GridDays,
    weekView: Boolean,
    firstDay: DayOfWeek
): File {
    val rootOrder = rows.map { it.rootId }.distinct()
    val rootIndexOf = rootOrder.withIndex().associate { it.value to it.index }
    val weekCellsByRow = rows.associate { it.nodeId to weekCellsOf(it.cells, firstDay) }
    val columnCount = if (weekView) {
        weekCellsByRow.values.firstOrNull()?.size ?: 0
    } else {
        days.size
    }
    val colW = if (weekView) WEEK_W else DAY_W

    val width = TITLE_W + columnCount * colW + MARGIN * 2
    val height = HEADER_H + rows.size.coerceAtLeast(1) * ROW_H + MARGIN * 2
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val titlePaint = Paint().apply { color = Color.BLACK; textSize = 24f; isAntiAlias = true }
    val headerPaint = Paint().apply { color = Color.DKGRAY; textSize = 20f; isAntiAlias = true }
    val emptyPaint = Paint().apply { color = 0xFFE0E0E0.toInt() }

    val dayFmt = DateTimeFormatter.ofPattern("d", Locale("es", "ES"))
    val weekFmt = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))

    // Cabecera de columnas
    if (weekView) {
        val weeks = weekCellsByRow.values.firstOrNull().orEmpty()
        weeks.forEachIndexed { i, cell ->
            val x = MARGIN + TITLE_W + i * colW
            canvas.drawText(
                LocalDate.ofEpochDay(cell.weekStart).format(weekFmt),
                x.toFloat() + 2f,
                MARGIN + HEADER_H - 10f,
                headerPaint
            )
        }
    } else {
        for (i in 0 until days.size) {
            val d = days.dayAt(i)
            val x = MARGIN + TITLE_W + i * colW
            canvas.drawText(
                LocalDate.ofEpochDay(d).format(dayFmt),
                x.toFloat() + 4f,
                MARGIN + HEADER_H - 10f,
                headerPaint
            )
        }
    }

    // Filas: titulo indentado y celdas
    rows.forEachIndexed { rowIndex, row ->
        val top = MARGIN + HEADER_H + rowIndex * ROW_H
        val rootIndex = rootIndexOf[row.rootId] ?: 0
        canvas.drawText(
            row.title,
            MARGIN + (row.depth * 16).toFloat() + 4f,
            top + ROW_H - 10f,
            titlePaint
        )
        val cells = if (weekView) weekCellsByRow[row.nodeId].orEmpty() else null
        for (i in 0 until columnCount) {
            val x = MARGIN + TITLE_W + i * colW
            val planned: Boolean
            val completed: Boolean
            if (weekView) {
                val cell = cells?.getOrNull(i)
                planned = cell?.planned == true
                completed = cell?.completed == true
            } else {
                val cell = row.cells.getOrNull(i)
                planned = cell?.planned == true
                completed = cell?.completed == true
            }
            val paint = if (!planned) {
                emptyPaint
            } else {
                Paint().apply { color = cellColor(rootIndex, row.depth, completed) }
            }
            canvas.drawRect(
                x.toFloat() + 1f,
                top.toFloat() + 1f,
                (x + colW).toFloat() - 1f,
                (top + ROW_H).toFloat() - 1f,
                paint
            )
        }
    }

    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

// Abre el selector de compartir para el PNG de la rejilla generado
fun shareGridPng(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
