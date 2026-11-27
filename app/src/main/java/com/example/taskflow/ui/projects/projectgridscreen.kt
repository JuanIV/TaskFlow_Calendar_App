package com.example.taskflow.ui.projects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskflow.R
import com.example.taskflow.domain.GridDays
import com.example.taskflow.domain.GridRow
import com.example.taskflow.domain.WeekCell
import com.example.taskflow.domain.cellColorFor
import com.example.taskflow.domain.weekCellsOf
import com.example.taskflow.domain.weekNumberOf
import com.example.taskflow.domain.weekStartOfDay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GRID_ROW_HEIGHT = 32.dp
private val GRID_TITLE_WIDTH = 200.dp
private val GRID_DAY_WIDTH = 28.dp
private val GRID_WEEK_WIDTH = 56.dp
private val GRID_HEADER_HEIGHT = 44.dp
private const val GRID_EXTEND_TRIGGER_DAYS = 14

// Rejilla virtualizada de un proyecto avanzado. Las filas son una LazyColumn; el desplazamiento
// horizontal es un unico estado compartido y cada Canvas solo dibuja las columnas visibles.
@OptIn(ExperimentalTextApi::class)
@Composable
fun ProjectGridView(
    rows: List<GridRow>,
    days: GridDays,
    weekView: Boolean,
    today: Long,
    firstDay: DayOfWeek,
    onToggleCollapse: (Long) -> Unit,
    editMode: Boolean = false,
    onCellTap: (nodeId: Long, epochDay: Long) -> Unit = { _, _ -> },
    treeMode: Boolean = false,
    onToggleCompleted: (Long, Boolean) -> Unit = { _, _ -> },
    onEditNode: (Long) -> Unit = {},
    onAddChild: (Long) -> Unit = {},
    onMoveNode: (Long) -> Unit = {},
    onDeleteNode: (Long) -> Unit = {},
    onSiblingMove: (Long, Int) -> Unit = { _, _ -> },
    weekOrigin: Long = 0L,
    onExtendForward: () -> Unit = {},
    onExtendBackward: () -> Unit = {}
) {
    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.p4_grid_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val density = LocalDensity.current
    val rootOrder = remember(rows) { rows.map { it.rootId }.distinct() }
    val rootIndexOf: Map<Long, Int> = remember(rootOrder) { rootOrder.withIndex().associate { it.value to it.index } }
    val dayStarts = remember(days, firstDay) { LongArray(days.size) { weekStartOfDay(days.dayAt(it), firstDay) } }
    val weekStarts = remember(dayStarts) { dayStarts.distinct() }
    val isWeekStart = remember(days, dayStarts) { BooleanArray(days.size) { dayStarts[it] == days.dayAt(it) } }
    val originWeekStart = remember(weekOrigin, firstDay) { weekStartOfDay(weekOrigin, firstDay) }

    val colWidth = if (weekView) GRID_WEEK_WIDTH else GRID_DAY_WIDTH
    val colPx = with(density) { colWidth.toPx() }
    val columns = if (weekView) weekStarts.size else days.size
    val totalPx = columns * colPx
    val todayCol = if (weekView) {
        weekStarts.indexOf(weekStartOfDay(today, firstDay))
    } else {
        (today - days.fromDay).toInt().let { if (it in 0 until days.size) it else -1 }
    }

    var viewportPx by remember { mutableIntStateOf(0) }
    val offset = remember { mutableFloatStateOf(0f) }
    val maxOffset = maxOf(0f, totalPx - viewportPx)
    val maxOffsetState = rememberUpdatedState(maxOffset)
    val offsetProvider: () -> Float = remember { { offset.floatValue } }
    val scrollState = rememberScrollableState { delta ->
        val old = offset.floatValue
        val next = (old - delta).coerceIn(0f, maxOffsetState.value)
        offset.floatValue = next
        old - next
    }

    // Posicion inicial: hoy cerca del borde izquierdo
    LaunchedEffect(weekView) {
        val index = if (weekView) maxOf(0, todayCol) else maxOf(0, todayCol)
        offset.floatValue = ((index - 2) * colPx).coerceIn(0f, maxOffsetState.value)
    }
    // Si se amplia por detras, el contenido se desplaza: se compensa el desplazamiento
    val prevFrom = remember { mutableLongStateOf(days.fromDay) }
    LaunchedEffect(days.fromDay) {
        val shift = prevFrom.longValue - days.fromDay
        if (shift > 0) {
            val columnsAdded = if (weekView) shift.toFloat() / 7f else shift.toFloat()
            offset.floatValue += columnsAdded * colPx
        }
        prevFrom.longValue = days.fromDay
    }
    LaunchedEffect(totalPx, viewportPx) {
        offset.floatValue = offset.floatValue.coerceIn(0f, maxOffset)
    }
    // Ampliacion automatica hacia delante al acercarse al final
    val extendForward = rememberUpdatedState(onExtendForward)
    LaunchedEffect(totalPx, viewportPx, weekView) {
        val dayPx = if (weekView) colPx / 7f else colPx
        snapshotFlow { offset.floatValue + viewportPx >= totalPx - GRID_EXTEND_TRIGGER_DAYS * dayPx }
            .distinctUntilChanged()
            .collect { near -> if (near) extendForward.value() }
    }

    Column(modifier = Modifier.fillMaxSize().scrollable(scrollState, Orientation.Horizontal)) {
        Row(modifier = Modifier.height(GRID_HEADER_HEIGHT)) {
            Box(
                modifier = Modifier.width(GRID_TITLE_WIDTH).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onExtendBackward) {
                    Text(stringResource(R.string.pt_grid_earlier), fontSize = 11.sp)
                }
            }
            GridHeaderCanvas(
                modifier = Modifier.weight(1f).fillMaxHeight().onSizeChanged { viewportPx = it.width },
                days = days,
                weekView = weekView,
                weekStarts = weekStarts,
                dayStarts = dayStarts,
                isWeekStart = isWeekStart,
                originWeekStart = originWeekStart,
                today = today,
                colPx = colPx,
                columns = columns,
                offset = offsetProvider
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(rows, key = { it.nodeId }) { row ->
                val weekCells = remember(row.cells, firstDay, weekView) {
                    if (weekView) weekCellsOf(row.cells, firstDay) else emptyList()
                }
                Row(modifier = Modifier.height(GRID_ROW_HEIGHT)) {
                    Box(modifier = Modifier.width(GRID_TITLE_WIDTH)) {
                        TitleRow(
                            row, rootIndexOf[row.rootId] ?: 0, editMode, treeMode, onToggleCollapse,
                            onToggleCompleted, onEditNode, onAddChild, onMoveNode, onDeleteNode, onSiblingMove
                        )
                    }
                    GridRowCanvas(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        row = row,
                        rootIndex = rootIndexOf[row.rootId] ?: 0,
                        weekView = weekView,
                        weekCells = weekCells,
                        colPx = colPx,
                        columns = columns,
                        isWeekStart = isWeekStart,
                        todayCol = todayCol,
                        editMode = editMode,
                        offset = offsetProvider,
                        onCellTap = onCellTap
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleRow(
    row: GridRow,
    rootIndex: Int,
    editMode: Boolean,
    treeMode: Boolean,
    onToggleCollapse: (Long) -> Unit,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onEditNode: (Long) -> Unit,
    onAddChild: (Long) -> Unit,
    onMoveNode: (Long) -> Unit,
    onDeleteNode: (Long) -> Unit,
    onSiblingMove: (Long, Int) -> Unit
) {
    val menu = remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .height(GRID_ROW_HEIGHT)
            .fillMaxWidth()
            .padding(start = (row.depth * 12).dp)
            .background(Color(cellColorFor(row.rootColorArgb, rootIndex, row.depth, false)).copy(alpha = 0.12f))
            .clickable(enabled = row.isGroup) { onToggleCollapse(row.nodeId) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.checkable) {
            Checkbox(
                checked = row.completed,
                onCheckedChange = { onToggleCompleted(row.nodeId, it) },
                enabled = !editMode
            )
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = row.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (row.isGroup) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        if (treeMode) {
            Box {
                IconButton(onClick = { menu.value = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.s2_more)
                    )
                }
                DropdownMenu(expanded = menu.value, onDismissRequest = { menu.value = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.p1c_action_edit)) },
                        onClick = { menu.value = false; onEditNode(row.nodeId) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.p1c_action_add_child)) },
                        onClick = { menu.value = false; onAddChild(row.nodeId) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.p1c_action_move)) },
                        onClick = { menu.value = false; onMoveNode(row.nodeId) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.p1c_action_up)) },
                        onClick = { menu.value = false; onSiblingMove(row.nodeId, -1) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.p1c_action_down)) },
                        onClick = { menu.value = false; onSiblingMove(row.nodeId, 1) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.p1c_action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu.value = false; onDeleteNode(row.nodeId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawCentered(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float
) {
    val result = measurer.measure(text, style)
    val x = (left + right) / 2f - result.size.width / 2f
    val y = (top + bottom) / 2f - result.size.height / 2f
    drawText(result, topLeft = Offset(x, y))
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun GridHeaderCanvas(
    modifier: Modifier,
    days: GridDays,
    weekView: Boolean,
    weekStarts: List<Long>,
    dayStarts: LongArray,
    isWeekStart: BooleanArray,
    originWeekStart: Long,
    today: Long,
    colPx: Float,
    columns: Int,
    offset: () -> Float
) {
    val measurer = rememberTextMeasurer()
    val resources = LocalContext.current.resources
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val strongColor = MaterialTheme.colorScheme.outline
    val normal = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val weekLabelStyle = remember(normal) { TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = normal) }
    val dayStyle = remember(muted) { TextStyle(fontSize = 10.sp, color = muted) }
    val todayStyle = remember(primary) { TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primary) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES")) }

    Canvas(modifier = modifier.clipToBounds()) {
        val off = offset()
        val w = size.width
        val h = size.height
        val half = h / 2f
        if (columns > 0) {
            val first = (off / colPx).toInt().coerceIn(0, columns - 1)
            val last = ((off + w) / colPx).toInt().coerceIn(0, columns - 1)
            if (weekView) {
                for (i in first..last) {
                    val x = i * colPx - off
                    val n = weekNumberOf(weekStarts[i], originWeekStart)
                    drawCentered(
                        measurer, resources.getString(R.string.pt_grid_week_short, n), weekLabelStyle,
                        x, x + colPx, 0f, half
                    )
                    drawCentered(
                        measurer, LocalDate.ofEpochDay(weekStarts[i]).format(dateFmt), dayStyle,
                        x, x + colPx, half, h
                    )
                    drawLine(strongColor, Offset(x, 0f), Offset(x, h), 3f)
                }
            } else {
                var i = first
                while (i <= last) {
                    val ws = dayStarts[i]
                    var s = i
                    while (s > 0 && dayStarts[s - 1] == ws) s--
                    var e = i
                    while (e + 1 < columns && dayStarts[e + 1] == ws) e++
                    val left = maxOf(s * colPx - off, 0f)
                    val right = minOf((e + 1) * colPx - off, w)
                    val n = weekNumberOf(ws, originWeekStart)
                    drawCentered(
                        measurer, resources.getString(R.string.pt_grid_week_label, n), weekLabelStyle,
                        left, right, 0f, half
                    )
                    i = e + 1
                }
                for (j in first..last) {
                    val x = j * colPx - off
                    val day = days.dayAt(j)
                    drawCentered(
                        measurer,
                        LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                        if (day == today) todayStyle else dayStyle,
                        x, x + colPx, half, h
                    )
                    if (isWeekStart[j]) {
                        drawLine(strongColor, Offset(x, 0f), Offset(x, h), 3f)
                    } else {
                        drawLine(lineColor, Offset(x, half), Offset(x, h), 1f)
                    }
                }
            }
        }
        drawLine(lineColor, Offset(0f, half), Offset(w, half), 1f)
        drawLine(strongColor, Offset(0f, h - 1f), Offset(w, h - 1f), 2f)
    }
}

@Composable
private fun GridRowCanvas(
    modifier: Modifier,
    row: GridRow,
    rootIndex: Int,
    weekView: Boolean,
    weekCells: List<WeekCell>,
    colPx: Float,
    columns: Int,
    isWeekStart: BooleanArray,
    todayCol: Int,
    editMode: Boolean,
    offset: () -> Float,
    onCellTap: (nodeId: Long, epochDay: Long) -> Unit
) {
    val pendingColor = Color(cellColorFor(row.rootColorArgb, rootIndex, row.depth, false))
    val doneColor = Color(cellColorFor(row.rootColorArgb, rootIndex, row.depth, true))
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val strongColor = MaterialTheme.colorScheme.outline
    val todayTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val rowState = rememberUpdatedState(row)
    val tapState = rememberUpdatedState(onCellTap)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(editMode, weekView, row.nodeId, row.checkable, colPx) {
                if (editMode && !weekView && row.checkable) {
                    detectTapGestures { tap ->
                        val index = ((tap.x + offset()) / colPx).toInt()
                        val current = rowState.value
                        current.cells.getOrNull(index)?.let { cell ->
                            tapState.value(current.nodeId, cell.epochDay)
                        }
                    }
                }
            }
    ) {
        if (columns <= 0) return@Canvas
        val off = offset()
        val h = size.height
        val first = (off / colPx).toInt().coerceIn(0, columns - 1)
        val last = ((off + size.width) / colPx).toInt().coerceIn(0, columns - 1)
        for (i in first..last) {
            val x = i * colPx - off
            val planned: Boolean
            val completed: Boolean
            if (weekView) {
                val cell = weekCells.getOrNull(i)
                planned = cell?.planned == true
                completed = cell?.completed == true
            } else {
                val cell = row.cells.getOrNull(i)
                planned = cell?.planned == true
                completed = cell?.completed == true
            }
            val color = when {
                planned && completed -> doneColor
                planned -> pendingColor
                else -> emptyColor
            }
            drawRect(color, Offset(x + 1f, 1f), Size(colPx - 2f, h - 2f))
        }
        if (todayCol in first..last) {
            drawRect(todayTint, Offset(todayCol * colPx - off, 0f), Size(colPx, h))
        }
        if (!weekView) {
            for (i in first..last) {
                if (isWeekStart[i]) {
                    val x = i * colPx - off
                    drawLine(strongColor, Offset(x, 0f), Offset(x, h), 3f)
                }
            }
        }
    }
}
