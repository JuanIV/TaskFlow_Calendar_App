package com.example.taskflow.ui.projects

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.domain.GanttRange
import com.example.taskflow.domain.GanttRow
import com.example.taskflow.domain.GanttZoom
import com.example.taskflow.domain.dayToX
import com.example.taskflow.domain.spanToWidth
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.barDays
import com.example.taskflow.domain.dayAfterMove
import com.example.taskflow.domain.minutesAfterResize
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ROW_HEIGHT = 36.dp
private val LIST_WIDTH = 160.dp
private val HEADER_HEIGHT = 28.dp
private val BAR_HEIGHT = 14.dp
private val GROUP_BAR_HEIGHT = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectGanttScreen(
    onBack: () -> Unit,
    viewModel: ProjectGanttViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val zoom by viewModel.zoom.collectAsState()
    var fullscreen by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<ProjectNodeEntity?>(null) }
    var showAddTask by remember { mutableStateOf(false) }
    var addTaskTitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Pantalla completa: fuerza orientacion apaisada y la restaura al salir
    DisposableEffect(fullscreen) {
        val activity = context as? Activity
        val previous = activity?.requestedOrientation
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (previous != null) activity.requestedOrientation = previous
        }
    }

    val current = state
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.p2b_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back))
                    }
                },
                actions = {
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(
                            if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.p2b_fullscreen)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTask = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.p2c_add_task_title))
            }
        }
    ) { innerPadding ->
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.p1c_loading))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GanttZoom.values().forEach { z ->
                        FilterChip(
                            selected = zoom == z,
                            onClick = { viewModel.setZoom(z) },
                            label = { Text(stringResource(zoomLabelRes(z))) }
                        )
                    }
                }
                GanttChart(
                    rows = current.rows,
                    range = current.range,
                    zoom = zoom,
                    today = current.today,
                    deadline = current.deadline,
                    onToggleGroup = { viewModel.toggleCollapsed(it) },
                    onMoveBar = { id, day -> viewModel.moveBar(id, day) { r -> showOpResult(context, r) } },
                    onResizeBar = { id, minutes -> viewModel.resizeBar(id, minutes) { r -> showOpResult(context, r) } },
                    onOpenNode = { id -> scope.launch { editingNode = viewModel.nodeById(id) } },
                    onAddRoot = { showAddTask = true },
                    hasNodes = current.rows.isNotEmpty()
                )
            }
        }
    }

    val editing = editingNode
    if (editing != null) {
        NodeFormDialog(
            existing = editing,
            isGroup = false,
            onDismiss = { editingNode = null },
            onSave = { draft ->
                scope.launch {
                    val result = viewModel.updateNodeFromGantt(editing.id, draft)
                    if (result == OpResult.OK) editingNode = null else showOpResult(context, result)
                }
            }
        )
    }

    if (showAddTask) {
        AlertDialog(
            onDismissRequest = { showAddTask = false },
            title = { Text(stringResource(R.string.p2c_add_task_title)) },
            text = {
                OutlinedTextField(
                    value = addTaskTitle,
                    onValueChange = { addTaskTitle = it },
                    label = { Text(stringResource(R.string.p2c_add_task_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = addTaskTitle.isNotBlank(),
                    onClick = {
                        val title = addTaskTitle
                        viewModel.addRootTask(title) { r ->
                            if (r == OpResult.OK) {
                                showAddTask = false
                                addTaskTitle = ""
                            } else {
                                showOpResult(context, r)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.p2c_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTask = false }) { Text(stringResource(R.string.p2c_cancel)) }
            }
        )
    }
}

private fun zoomLabelRes(zoom: GanttZoom): Int = when (zoom) {
    GanttZoom.DAY -> R.string.p2b_zoom_day
    GanttZoom.WEEK -> R.string.p2b_zoom_week
    GanttZoom.MONTH -> R.string.p2b_zoom_month
}

@Composable
private fun GanttChart(
    rows: List<GanttRow>,
    range: GanttRange,
    zoom: GanttZoom,
    today: Long,
    deadline: Long?,
    onToggleGroup: (Long) -> Unit,
    onMoveBar: (Long, Long) -> Unit,
    onResizeBar: (Long, Int) -> Unit,
    onOpenNode: (Long) -> Unit,
    onAddRoot: () -> Unit,
    hasNodes: Boolean
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dayWidthPx = with(density) { zoom.dayWidthDp.dp.toPx() }
    val totalWidthPx = range.days * dayWidthPx
    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }
    val barHeightPx = with(density) { BAR_HEIGHT.toPx() }
    val groupBarHeightPx = with(density) { GROUP_BAR_HEIGHT.toPx() }

    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()

    val colorTodayLine = MaterialTheme.colorScheme.primary
    val colorDeadline = Color.Red
    val colorBarBg = MaterialTheme.colorScheme.surfaceVariant
    val colorBarFill = MaterialTheme.colorScheme.primary
    val colorGroupFill = MaterialTheme.colorScheme.outline
    val colorCritical = Color.Red
    val colorDependency = MaterialTheme.colorScheme.onSurfaceVariant
    val colorGrid = MaterialTheme.colorScheme.outlineVariant

    Row(modifier = Modifier.fillMaxSize()) {
        // Lista fija a la izquierda, con desplazamiento vertical sincronizado
        Column(modifier = Modifier.width(LIST_WIDTH)) {
            Box(modifier = Modifier.height(HEADER_HEIGHT).fillMaxWidth())
            Column(modifier = Modifier.verticalScroll(vertical)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .height(ROW_HEIGHT)
                            .fillMaxWidth()
                            .padding(start = (row.depth * 8).dp)
                            .clickable(enabled = row.isGroup) { onToggleGroup(row.nodeId) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (row.isGroup) FontWeight.Bold else FontWeight.Normal,
                            color = if (row.critical) colorCritical else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Area de barras: desplazamiento horizontal y vertical
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.horizontalScroll(horizontal)) {
                DateHeader(range = range, dayWidthDp = zoom.dayWidthDp, height = HEADER_HEIGHT)
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .verticalScroll(vertical)
                ) {
                    // Capa de barras: pulsar para abrir, arrastrar para mover o redimensionar
                    Box(modifier = Modifier.width(totalWidthDp).height(ROW_HEIGHT * rows.size.coerceAtLeast(1))) {
                        rows.forEachIndexed { index, row ->
                            val s = row.startDay
                            val e = row.endDay
                            if (s != null && e != null && !row.isGroup) {
                                EditableBar(
                                    rowIndex = index,
                                    row = row,
                                    startDay = s,
                                    endDay = e,
                                    today = today,
                                    dayWidthPx = dayWidthPx,
                                    range = range,
                                    onMove = { newDay -> onMoveBar(row.nodeId, newDay) },
                                    onResize = { newMinutes -> onResizeBar(row.nodeId, newMinutes) },
                                    onOpen = { onOpenNode(row.nodeId) }
                                )
                            }
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .width(totalWidthDp)
                            .height(ROW_HEIGHT * rows.size.coerceAtLeast(1))
                    ) {
                        val step = if (zoom == GanttZoom.DAY) 1 else if (zoom == GanttZoom.WEEK) 7 else 30
                        var d = 0L
                        while (d < range.days) {
                            val x = d * dayWidthPx
                            drawLine(colorGrid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            d += step
                        }
                        val todayX = dayToX(today, range, dayWidthPx) + dayWidthPx / 2f
                        drawLine(colorTodayLine, Offset(todayX, 0f), Offset(todayX, size.height), strokeWidth = 2f)
                        if (deadline != null) {
                            val dx = dayToX(deadline, range, dayWidthPx) + dayWidthPx / 2f
                            drawLine(colorDeadline, Offset(dx, 0f), Offset(dx, size.height), strokeWidth = 2f)
                        }

                        val starts = HashMap<Long, Offset>()
                        val ends = HashMap<Long, Offset>()
                        rows.forEachIndexed { index, row ->
                            val rowTop = index * rowHeightPx
                            val centerY = rowTop + rowHeightPx / 2f
                            val s = row.startDay
                            val e = row.endDay
                            if (row.isMilestone && s != null) {
                                val x = dayToX(s, range, dayWidthPx) + dayWidthPx / 2f
                                val r = barHeightPx / 2f
                                val diamond = Path().apply {
                                    moveTo(x, centerY - r)
                                    lineTo(x + r, centerY)
                                    lineTo(x, centerY + r)
                                    lineTo(x - r, centerY)
                                    close()
                                }
                                drawPath(diamond, colorBarFill)
                                starts[row.nodeId] = Offset(x - r, centerY)
                                ends[row.nodeId] = Offset(x + r, centerY)
                            } else if (s != null && e != null) {
                                val x = dayToX(s, range, dayWidthPx)
                                val w = spanToWidth(s, e, dayWidthPx)
                                val h = if (row.isGroup) groupBarHeightPx else barHeightPx
                                val top = centerY - h / 2f
                                drawRect(colorBarBg, Offset(x, top), Size(w, h))
                                val fillW = w * row.progressPercent / 100f
                                drawRect(if (row.isGroup) colorGroupFill else colorBarFill, Offset(x, top), Size(fillW, h))
                                if (row.critical) {
                                    drawRect(colorCritical, Offset(x, top), Size(w, h), style = Stroke(width = 2f))
                                }
                                starts[row.nodeId] = Offset(x, centerY)
                                ends[row.nodeId] = Offset(x + w, centerY)
                            }
                        }

                        // Dependencias: desde el final del predecesor hasta el inicio del sucesor
                        rows.forEach { row ->
                            val to = starts[row.nodeId] ?: return@forEach
                            row.predecessorIds.forEach { predId ->
                                val from = ends[predId] ?: return@forEach
                                val path = Path().apply {
                                    moveTo(from.x, from.y)
                                    val midX = (from.x + to.x) / 2f
                                    lineTo(midX, from.y)
                                    lineTo(midX, to.y)
                                    lineTo(to.x, to.y)
                                }
                                drawPath(path, colorDependency, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableBar(
    rowIndex: Int,
    row: GanttRow,
    startDay: Long,
    endDay: Long,
    today: Long,
    dayWidthPx: Float,
    range: GanttRange,
    onMove: (Long) -> Unit,
    onResize: (Int) -> Unit,
    onOpen: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }
    val barHeightPx = with(density) { BAR_HEIGHT.toPx() }
    val handleWidthPx = with(density) { 14.dp.toPx() }
    val oldDays = barDays(startDay, endDay)
    val barX = dayToX(startDay, range, dayWidthPx)
    val barW = spanToWidth(startDay, endDay, dayWidthPx)
    val barTop = rowIndex * rowHeightPx + (rowHeightPx - barHeightPx) / 2f

    var dragDx by remember { mutableStateOf(0f) }
    var resizeDx by remember { mutableStateOf(0f) }

    // Zona de movimiento: la barra completa menos el asa derecha
    Box(
        modifier = Modifier
            .absoluteOffset { IntOffset(barX.roundToInt(), barTop.roundToInt()) }
            .size(
                with(density) { (barW - handleWidthPx).coerceAtLeast(8f).toDp() },
                with(density) { barHeightPx.toDp() }
            )
            .offset { IntOffset(dragDx.roundToInt(), 0) }
            .background(Color.Transparent)
            .pointerInput(row.nodeId, dayWidthPx) {
                detectTapGestures(onTap = { onOpen() })
            }
            .pointerInput(row.nodeId, dayWidthPx) {
                detectDragGestures(
                    onDragStart = { dragDx = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDx += amount.x
                    },
                    onDragEnd = {
                        val newDay = dayAfterMove(startDay, dragDx, dayWidthPx, today)
                        dragDx = 0f
                        if (newDay != startDay) onMove(newDay)
                    },
                    onDragCancel = { dragDx = 0f }
                )
            }
    ) { }

    // Asa derecha: cambia la duracion
    Box(
        modifier = Modifier
            .absoluteOffset { IntOffset((barX + barW - handleWidthPx).roundToInt(), barTop.roundToInt()) }
            .size(
                with(density) { handleWidthPx.toDp() },
                with(density) { barHeightPx.toDp() }
            )
            .offset { IntOffset(resizeDx.roundToInt(), 0) }
            .background(Color.Transparent)
            .pointerInput(row.nodeId, dayWidthPx) {
                detectDragGestures(
                    onDragStart = { resizeDx = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        resizeDx += amount.x
                    },
                    onDragEnd = {
                        val newMinutes = minutesAfterResize(
                            currentMinutes = row.estimatedMinutes,
                            oldDays = oldDays,
                            dxPx = resizeDx,
                            dayWidthPx = dayWidthPx
                        )
                        resizeDx = 0f
                        if (newMinutes != row.estimatedMinutes) onResize(newMinutes)
                    },
                    onDragCancel = { resizeDx = 0f }
                )
            }
    ) { }
}

@Composable
private fun DateHeader(range: GanttRange, dayWidthDp: Int, height: androidx.compose.ui.unit.Dp) {
    val fmt = DateTimeFormatter.ofPattern("d MMM", java.util.Locale("es", "ES"))
    val labelEvery = when {
        dayWidthDp >= 24 -> 1L
        dayWidthDp >= 8 -> 7L
        else -> 30L
    }
    Box(modifier = Modifier.height(height).width(dayWidthDp.dp * range.days.toInt())) {
        var d = 0L
        while (d < range.days) {
            val day = LocalDate.ofEpochDay(range.fromDay + d).format(fmt)
            Text(
                text = day,
                fontSize = 10.sp,
                modifier = Modifier
                    .padding(start = ((dayWidthDp * d).toInt()).dp)
                    .requiredWidth(((dayWidthDp * labelEvery).toInt()).dp)
            )
            d += labelEvery
        }
    }
}
