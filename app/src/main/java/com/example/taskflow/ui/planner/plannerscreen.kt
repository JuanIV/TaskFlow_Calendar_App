package com.example.taskflow.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.ui.calendar.weekStartOf
import com.example.taskflow.ui.tasks.durationText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private fun toLocal(container: LayoutCoordinates?, rootPoint: Offset): Offset? {
    if (container == null || !container.isAttached) return null
    return rootPoint - container.localToRoot(Offset.Zero)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    onBack: () -> Unit,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val firstDay by viewModel.firstDay.collectAsState()
    val todayEpoch by viewModel.todayEpoch.collectAsState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scroll = rememberScrollState()
    val esLocale = remember { Locale("es", "ES") }
    val movedMsg = stringResource(R.string.s2_task_moved)
    val undoLabel = stringResource(R.string.s2_undo)

    var weekOffset by remember { mutableStateOf(0) }
    var dragTask by remember { mutableStateOf<TaskEntity?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val today = LocalDate.ofEpochDay(todayEpoch)
    val weekStart = weekStartOf(today.plusWeeks(weekOffset.toLong()), firstDay)
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekEnd = weekStart.plusDays(6)
    val weekTitle = weekStart.format(DateTimeFormatter.ofPattern("d MMM", esLocale)) + " - " +
        weekEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy", esLocale))

    val colPx = with(density) { 140.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val padPx = with(density) { 8.dp.toPx() }
    val edgePx = with(density) { 48.dp.toPx() }

    fun hoverIndex(): Int? {
        val local = toLocal(containerCoords, pointer) ?: return null
        if (local.x < 0f || local.y < 0f || local.x > containerSize.width || local.y > containerSize.height) return null
        return plannerColumnIndex(local.x + scroll.value, padPx, colPx, gapPx, 8)
    }

    LaunchedEffect(dragTask?.id) {
        while (dragTask != null) {
            val local = toLocal(containerCoords, pointer)
            if (local != null) {
                val dx = when {
                    local.x < edgePx -> -16f
                    local.x > containerSize.width - edgePx -> 16f
                    else -> 0f
                }
                if (dx != 0f) scroll.scrollBy(dx)
            }
            delay(16)
        }
    }

    val onStart: (TaskEntity, Offset) -> Unit = { task, rootPoint ->
        dragTask = task
        pointer = rootPoint
    }
    val onMove: (Offset) -> Unit = { delta -> pointer = pointer + delta }
    val onEnd: () -> Unit = {
        val task = dragTask
        val idx = hoverIndex()
        dragTask = null
        if (task != null && idx != null) {
            val newDate: Long? = if (idx == 0) null else weekStart.toEpochDay() + (idx - 1)
            if (newDate != task.date) {
                viewModel.move(task, newDate)
                scope.launch {
                    val r = snackbarHostState.showSnackbar(message = movedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) viewModel.restore(task)
                }
            }
        }
    }
    val onCancel: () -> Unit = { dragTask = null }

    val hover = if (dragTask != null) hoverIndex() else null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.s3_planner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { weekOffset = weekOffset - 1 }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.s2_prev))
                }
                Text(
                    weekTitle,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { weekOffset = 0 }) { Text(stringResource(R.string.s2_today)) }
                IconButton(onClick = { weekOffset = weekOffset + 1 }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.s2_next))
                }
            }
            Text(
                stringResource(R.string.s3_planner_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { containerCoords = it; containerSize = it.size }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().horizontalScroll(scroll).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlannerColumn(
                        title = stringResource(R.string.s3_planner_unscheduled),
                        isToday = false,
                        highlighted = hover == 0,
                        tasks = tasks.filter { it.date == null && !it.isCompleted },
                        draggingId = dragTask?.id,
                        onDragStart = onStart,
                        onDrag = onMove,
                        onDragEnd = onEnd,
                        onDragCancel = onCancel
                    )
                    days.forEachIndexed { i, date ->
                        val epoch = date.toEpochDay()
                        PlannerColumn(
                            title = date.format(DateTimeFormatter.ofPattern("EEE d", esLocale)).replaceFirstChar { it.uppercase() },
                            isToday = epoch == todayEpoch,
                            highlighted = hover == i + 1,
                            tasks = tasks.filter { it.date == epoch }
                                .sortedWith(compareBy<TaskEntity>({ it.isCompleted }, { -it.priority }, { it.sortOrder })),
                            draggingId = dragTask?.id,
                            onDragStart = onStart,
                            onDrag = onMove,
                            onDragEnd = onEnd,
                            onDragCancel = onCancel
                        )
                    }
                }
                val dragging = dragTask
                if (dragging != null) {
                    val local = toLocal(containerCoords, pointer)
                    if (local != null) {
                        Card(
                            modifier = Modifier
                                .width(140.dp)
                                .offset { IntOffset((local.x - colPx / 2f).roundToInt(), (local.y - 24.dp.toPx()).roundToInt()) }
                                .alpha(0.9f)
                        ) {
                            Text(
                                dragging.title,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerColumn(
    title: String,
    isToday: Boolean,
    highlighted: Boolean,
    tasks: List<TaskEntity>,
    draggingId: Long?,
    onDragStart: (TaskEntity, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val estimated = tasks.filter { !it.isCompleted }.sumOf { it.estimatedMinutes ?: 0 }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(140.dp)
            .fillMaxHeight()
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape
            )
            .then(if (isToday || highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .padding(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (estimated > 0) {
            Text(
                "~" + durationText(estimated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tasks.forEach { task ->
                PlannerTaskCard(
                    task = task,
                    hidden = task.id == draggingId,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
        }
    }
}

@Composable
private fun PlannerTaskCard(
    task: TaskEntity,
    hidden: Boolean,
    onDragStart: (TaskEntity, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val startCb by rememberUpdatedState(onDragStart)
    val dragCb by rememberUpdatedState(onDrag)
    val endCb by rememberUpdatedState(onDragEnd)
    val cancelCb by rememberUpdatedState(onDragCancel)
    val currentTask by rememberUpdatedState(task)

    val gesture = if (!task.isCompleted) {
        Modifier.pointerInput(task.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val c = coords
                    if (c != null && c.isAttached) startCb(currentTask, c.localToRoot(offset))
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragCb(dragAmount)
                },
                onDragEnd = { endCb() },
                onDragCancel = { cancelCb() }
            )
        }
    } else {
        Modifier
    }

    val dueMinute = task.dueMinuteOfDay
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (hidden) 0.3f else if (task.isCompleted) 0.6f else 1f)
            .onGloballyPositioned { coords = it }
            .then(gesture)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
            if (dueMinute != null) {
                Text(formatMinute(dueMinute), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
