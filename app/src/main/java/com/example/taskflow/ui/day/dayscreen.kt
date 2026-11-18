package com.example.taskflow.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.ui.calendar.CalendarViewModel
import com.example.taskflow.ui.calendar.getTextColorForBackground
import com.example.taskflow.ui.calendar.sortOccurrences
import com.example.taskflow.ui.events.EventDialog
import com.example.taskflow.ui.tasks.TaskDialog
import com.example.taskflow.ui.tasks.TasksViewModel
import com.example.taskflow.ui.tasks.durationText
import com.example.taskflow.ui.components.IconKeys
import com.example.taskflow.domain.formatMinute
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    epochDay: Long,
    onBack: () -> Unit,
    onEventClick: (Long, Long) -> Unit,
    calendarViewModel: CalendarViewModel = hiltViewModel(),
    tasksViewModel: TasksViewModel = hiltViewModel()
) {
    val date = LocalDate.ofEpochDay(epochDay)
    val flow = remember(epochDay) { calendarViewModel.occurrencesFor(epochDay, epochDay) }
    val occurrences by flow.collectAsState(initial = emptyList())
    val events = remember(occurrences) { sortOccurrences(occurrences) }
    val allTasks by tasksViewModel.allActiveTasks.collectAsState()
    val dayTasks = allTasks.filter { it.parentTaskId == null && it.date == epochDay }
    val estimatedTotal = dayTasks.filter { !it.isCompleted }.sumOf { it.estimatedMinutes ?: 0 }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMsg = stringResource(R.string.s2_task_deleted)
    val undoLabel = stringResource(R.string.s2_undo)

    var fabExpanded by remember { mutableStateOf(false) }
    var showNewEvent by remember { mutableStateOf(false) }
    var showNewTask by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }

    val title = date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es", "ES")))
        .replaceFirstChar { it.uppercase() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (fabExpanded) {
                    ExtendedFloatingActionButton(
                        onClick = { fabExpanded = false; showNewEvent = true },
                        icon = { Icon(Icons.Default.Event, contentDescription = null) },
                        text = { Text(stringResource(R.string.s2_new_event)) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ExtendedFloatingActionButton(
                        onClick = { fabExpanded = false; showNewTask = true },
                        icon = { Icon(Icons.Default.Task, contentDescription = null) },
                        text = { Text(stringResource(R.string.s2_new_task)) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.s2_add))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            item {
                Text(stringResource(R.string.s2_events), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (events.isEmpty()) {
                item {
                    Text(stringResource(R.string.s2_no_events_day), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(events, key = { "${it.eventId}_${it.originalDate}" }) { event ->
                    DayEventItem(event = event, onClick = { onEventClick(event.eventId, event.originalDate) })
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.s2_tasks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (estimatedTotal > 0) {
                    Text(
                        stringResource(R.string.s3_task_total_time, durationText(estimatedTotal)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (dayTasks.isEmpty()) {
                item {
                    Text(stringResource(R.string.s2_no_tasks_day), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(dayTasks, key = { "t${it.id}" }) { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { editingTask = task },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = task.isCompleted, onCheckedChange = { tasksViewModel.toggleTaskCompletion(task) })
                        Text(
                            task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }

    if (showNewEvent) {
        EventDialog(initialDate = date, onDismiss = { showNewEvent = false })
    }

    if (showNewTask || editingTask != null) {
        val editing = editingTask
        TaskDialog(
            initialDate = epochDay,
            taskToEdit = editing,
            existingSubtasks = if (editing != null) allTasks.filter { it.parentTaskId == editing.id }.sortedBy { it.sortOrder } else emptyList(),
            onDismiss = { showNewTask = false; editingTask = null },
            onSave = { result -> tasksViewModel.saveTaskFromResult(result) },
            onDelete = { id ->
                tasksViewModel.deleteTask(id)
                showNewTask = false
                editingTask = null
                scope.launch {
                    val r = snackbarHostState.showSnackbar(message = deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) tasksViewModel.restoreTask(id)
                }
            },
            onToggleSubtask = { tasksViewModel.toggleTaskCompletion(it) },
            onDeleteSubtask = { tasksViewModel.deleteTask(it) }
        )
    }
}

@Composable
private fun DayEventItem(event: EventOccurrence, onClick: () -> Unit) {
    val base = Color(event.colorArgb)
    val secondary = event.priority == "SECONDARY"
    val textColor = if (secondary) MaterialTheme.colorScheme.onSurface else getTextColorForBackground(base)
    val start = event.startMinute
    val end = event.endMinute
    val timeText = if (event.allDay || start == null) {
        stringResource(R.string.s2_all_day)
    } else {
        formatMinute(start) +
                (if (end != null) " - " + formatMinute(end) else "")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (secondary) base.copy(alpha = 0.45f) else base, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconVector = IconKeys.iconFor(event.icon)
                if (iconVector != null) {
                    Icon(iconVector, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(timeText, style = MaterialTheme.typography.labelLarge, color = textColor, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                Text(event.title, style = MaterialTheme.typography.bodyLarge, color = textColor)
            }
            if (secondary) {
                Text(stringResource(R.string.s2_secondary_tag), style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        }
    }
}

