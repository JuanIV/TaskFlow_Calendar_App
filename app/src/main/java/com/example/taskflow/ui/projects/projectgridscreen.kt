package com.example.taskflow.ui.projects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskflow.domain.GridDays
import com.example.taskflow.domain.GridRow
import com.example.taskflow.domain.cellColor
import com.example.taskflow.domain.rootBaseColor
import com.example.taskflow.domain.weekStartOfDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.taskflow.R
import com.example.taskflow.domain.weekCellsOf

private val GRID_ROW_HEIGHT = 32.dp
private val GRID_TITLE_WIDTH = 200.dp
private val GRID_DAY_WIDTH = 28.dp
private val GRID_WEEK_WIDTH = 56.dp
private val GRID_HEADER_HEIGHT = 44.dp

// Rejilla de un proyecto avanzado. Solo lectura en el paquete 4.
// rootIndexOf: mapa rootId -> indice de raiz, para elegir el color base.
@Composable
fun ProjectGridView(
    rows: List<GridRow>,
    days: GridDays,
    weekView: Boolean,
    today: Long,
    firstDay: DayOfWeek,
    onToggleCollapse: (Long) -> Unit,
    editMode: Boolean = false,
    onCellTap: (nodeId: Long, epochDay: Long) -> Unit = { _, _ -> }
) {
    val rootOrder = rows.map { it.rootId }.distinct()
    val rootIndexOf: Map<Long, Int> = rootOrder.withIndex().associate { it.value to it.index }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val weekStarts = remember(days, firstDay) {
        (0 until days.size).map { weekStartOfDay(days.dayAt(it), firstDay) }.distinct()
    }
    val colWidth = if (weekView) GRID_WEEK_WIDTH else GRID_DAY_WIDTH

    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.p4_grid_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Columna fija: cabecera y titulos con desplazamiento vertical sincronizado
        Column(modifier = Modifier.width(GRID_TITLE_WIDTH)) {
            Box(modifier = Modifier.height(GRID_HEADER_HEIGHT).fillMaxWidth())
            Column(modifier = Modifier.verticalScroll(vertical)) {
                rows.forEach { row ->
                    TitleRow(row, rootIndexOf[row.rootId] ?: 0, onToggleCollapse)
                }
            }
        }

        // Cuadricula con desplazamiento horizontal y vertical
        Column(modifier = Modifier.horizontalScroll(horizontal)) {
            GridHeader(days, weekView, weekStarts, firstDay, today, colWidth)
            Column(modifier = Modifier.verticalScroll(vertical)) {
                rows.forEach { row ->
                    GridRowCells(row, days, weekView, weekStarts, rootIndexOf[row.rootId] ?: 0, colWidth, firstDay, editMode, onCellTap)
                }
            }
        }
    }
}

@Composable
private fun TitleRow(row: GridRow, rootIndex: Int, onToggleCollapse: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .height(GRID_ROW_HEIGHT)
            .fillMaxWidth()
            .padding(start = (row.depth * 12).dp)
            .background(Color(cellColor(rootIndex, row.depth, completed = false)).copy(alpha = 0.12f))
            .clickable(enabled = row.isGroup) { onToggleCollapse(row.nodeId) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.checkable) {
            // Checkbox visible pero no editable hasta el paquete 5
            Checkbox(checked = row.completed, onCheckedChange = null, enabled = false)
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = row.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (row.isGroup) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun GridHeader(
    days: GridDays,
    weekView: Boolean,
    weekStarts: List<Long>,
    firstDay: DayOfWeek,
    today: Long,
    colWidth: androidx.compose.ui.unit.Dp
) {
    val dayFmt = DateTimeFormatter.ofPattern("d", Locale("es", "ES"))
    val weekFmt = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))
    Row(modifier = Modifier.height(GRID_HEADER_HEIGHT)) {
        if (weekView) {
            weekStarts.forEach { ws ->
                Column(
                    modifier = Modifier.width(colWidth).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(LocalDate.ofEpochDay(ws).format(weekFmt), fontSize = 10.sp)
                }
            }
        } else {
            (0 until days.size).forEach { i ->
                val d = days.dayAt(i)
                val isToday = d == today
                Column(
                    modifier = Modifier.width(colWidth).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        LocalDate.ofEpochDay(d).format(dayFmt),
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (weekStarts.contains(d) || i == 0) {
                        Text(
                            LocalDate.ofEpochDay(d).format(weekFmt),
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridRowCells(
    row: GridRow,
    days: GridDays,
    weekView: Boolean,
    weekStarts: List<Long>,
    rootIndex: Int,
    colWidth: androidx.compose.ui.unit.Dp,
    firstDay: DayOfWeek,
    editMode: Boolean,
    onCellTap: (nodeId: Long, epochDay: Long) -> Unit
) {
    val pendingColor = Color(cellColor(rootIndex, row.depth, completed = false))
    val doneColor = Color(cellColor(rootIndex, row.depth, completed = true))
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val weekCells = remember(row.cells, firstDay) { weekCellsOf(row.cells, firstDay) }
    val columns = if (weekView) weekCells.size else row.cells.size
    val density = LocalDensity.current
    Canvas(
        modifier = Modifier
            .height(GRID_ROW_HEIGHT)
            .width(colWidth * columns)
            .pointerInput(editMode, weekView, row.nodeId, row.checkable) {
                if (editMode && !weekView && row.checkable) {
                    detectTapGestures { offset ->
                        val cw = with(density) { colWidth.toPx() }
                        val index = (offset.x / cw).toInt()
                        row.cells.getOrNull(index)?.let { cell ->
                            onCellTap(row.nodeId, cell.epochDay)
                        }
                    }
                }
            }
    ) {
        val cw = with(density) { colWidth.toPx() }
        val ch = with(density) { GRID_ROW_HEIGHT.toPx() }
        if (weekView) {
            weekCells.forEachIndexed { i, cell ->
                val color = when {
                    cell.planned && cell.completed -> doneColor
                    cell.planned -> pendingColor
                    else -> emptyColor
                }
                drawRect(color, Offset(i * cw + 1f, 1f), Size(cw - 2f, ch - 2f))
            }
        } else {
            row.cells.forEachIndexed { i, cell ->
                val color = when {
                    cell.planned && cell.completed -> doneColor
                    cell.planned -> pendingColor
                    else -> emptyColor
                }
                drawRect(color, Offset(i * cw + 1f, 1f), Size(cw - 2f, ch - 2f))
            }
        }
    }
}
