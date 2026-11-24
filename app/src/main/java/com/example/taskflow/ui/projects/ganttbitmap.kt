package com.example.taskflow.ui.projects

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.example.taskflow.domain.GanttRange
import com.example.taskflow.domain.GanttRow
import com.example.taskflow.domain.GanttZoom
import com.example.taskflow.domain.dayToX
import com.example.taskflow.domain.spanToWidth
import java.io.File
import java.io.FileOutputStream

// Dibuja un PNG con las filas y barras del Gantt (sin hoy ni deadline ni dependencias)
fun renderGanttPng(
    context: Context,
    fileName: String,
    rows: List<GanttRow>,
    range: GanttRange,
    zoom: GanttZoom
): File {
    val rowH = 40
    val labelW = 360
    val dayW = zoom.dayWidthDp * 2f
    val width = labelW + (range.days * dayW).toInt() + 20
    val height = rowH * (rows.size.coerceAtLeast(1)) + 20
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val text = Paint().apply { color = Color.BLACK; textSize = 26f; isAntiAlias = true }
    val bar = Paint().apply { color = 0xFF3949AB.toInt() }
    val group = Paint().apply { color = 0xFF9E9E9E.toInt() }
    val bg = Paint().apply { color = 0xFFEEEEEE.toInt() }
    rows.forEachIndexed { i, row ->
        val top = 10f + i * rowH
        canvas.drawText(row.title, 8f + row.depth * 16f, top + 28f, text)
        val s = row.startDay
        val e = row.endDay
        if (s != null && e != null) {
            val x = labelW + dayToX(s, range, dayW)
            val w = spanToWidth(s, e, dayW)
            canvas.drawRect(x, top + 10f, x + w, top + 28f, bg)
            canvas.drawRect(x, top + 10f, x + w * row.progressPercent / 100f, top + 28f, if (row.isGroup) group else bar)
        }
    }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

// Abre el selector de compartir para el PNG generado
fun sharePng(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
