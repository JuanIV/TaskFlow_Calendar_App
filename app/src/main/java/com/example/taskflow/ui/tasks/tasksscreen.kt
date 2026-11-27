package com.example.taskflow.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewWeek
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
import com.example.taskflow.domain.isProjectDateLocked
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.ui.components.CustomDatePickerDialog
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel(),
    onOpenSearch: () -> Unit = {},
    onOpenFocus: (Long) -> Unit = {},
    onOpenPlanner: () -> Unit = {}
) {
    val allTasks by viewModel.allActiveTasks.collectAsState()
    val todayEpoch by viewModel.todayEpoch.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var taskEditing by remember { mutableStateOf<TaskEntity?>(null) }
    var completedExpanded by remember { mutableStateOf(false) }
    var parentPrompt by remember { mutableStateOf<TaskEntity?>(null) }
    var subtaskParent by remember { mutableStateOf<TaskEntity?>(null) }
    var pickDateTask by remember { mutableStateOf<TaskEntity?>(null) }
    var advancePrompt by remember { mutableStateOf<TaskEntity?>(null) }

    val deletedMsg = stringResource(R.string.s2_task_deleted)
    val movedMsg = stringResource(R.string.s2_task_moved)
    val undoLabel = stringResource(R.string.s2_undo)

    val topLevel = allTasks.filter { it.parentTaskId == null }
    fun subtasksOf(id: Long): List<TaskEntity> = allTasks.filter { it.parentTaskId == id }.sortedBy { it.sortOrder }
    fun ordered(list: List<TaskEntity>): List<TaskEntity> =
        if (sortMode == "PRIORITY") list.sortedByDescending { it.priority } else list

    val overdue = ordered(topLevel.filter { val d = it.date; d != null && d < todayEpoch && !it.isCompleted })
    val todayList = ordered(topLevel.filter { it.date == todayEpoch }).sortedBy { it.isCompleted }
    val futureList = ordered(topLevel.filter { val d = it.date; d != null && d > todayEpoch })
    val looseAll = topLevel.filter { it.date == null }
    val loosePending = ordered(looseAll.filter { !it.isCompleted })
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val looseCompleted = looseAll.filter { it.isCompleted && (it.completedAt ?: 0L) >= weekAgo }

    fun handleToggle(task: TaskEntity) {
        if (!task.isCompleted && subtasksOf(task.id).any { !it.isCompleted }) {
            parentPrompt = task
        } else {
            scope.launch {
                if (viewModel.isPlanAdvance(task, todayEpoch)) {
                    advancePrompt = task
                } else {
                    viewModel.toggleTaskCompletion(task)
                }
            }
        }
    }

    fun handleDeleteId(id: Long) {
        viewModel.deleteTask(id)
        scope.launch {
            val r = snackbarHostState.showSnackbar(message = deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) viewModel.restoreTask(id)
        }
    }

    fun handleMove(task: TaskEntity, newDate: Long) {
        viewModel.postponeTask(task, newDate)
        scope.launch {
            val r = snackbarHostState.showSnackbar(message = movedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) viewModel.restoreTaskState(task)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyColumnState(lazyListState) { from, to ->
        if (sortMode == "MANUAL") {
            val fromId = from.key as? Long
            val toId = to.key as? Long
            val fromIdx = loosePending.indexOfFirst { it.id == fromId }
            val toIdx = loosePending.indexOfFirst { it.id == toId }
            if (fromIdx >= 0 && toIdx >= 0) {
                val mutable = loosePending.toMutableList()
                mutable.add(toIdx, mutable.removeAt(fromIdx))
                viewModel.reorderTasks(mutable)
            }
        }
    }

    val card: @Composable (TaskEntity, Boolean, Modifier?) -> Unit = { task, dragging, handle ->
        TaskCard(
            task = task,
            subtasks = subtasksOf(task.id),
            todayEpoch = todayEpoch,
            isDragging = dragging,
            dragModifier = handle,
            onToggle = { handleToggle(task) },
            onClick = { taskEditing = task; showDialog = true },
            onDelete = { if (task.projectKey == null) handleDeleteId(task.id) },
            onMoveTo = { if (!task.isProjectDateLocked()) handleMove(task, it) },
            onPickDate = { if (!task.isProjectDateLocked()) pickDateTask = task },
            onAddSubtask = { subtaskParent = task },
            onStartFocus = {
                val projectNode = task.projectNodeId
                if (projectNode != null) {
                    viewModel.startProjectFocus(projectNode)
                    onOpenFocus(-1L)
                } else {
                    onOpenFocus(task.id)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_tasks)) },
                actions = {
                    IconButton(onClick = onOpenPlanner) {
                        Icon(Icons.Default.ViewWeek, contentDescription = stringResource(R.string.s3_planner_title))
                    }
                    IconButton(onClick = { onOpenFocus(-1L) }) {
                        Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.s3_focus_title))
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.s2_search))
                    }
                    TextButton(onClick = { viewModel.setSortMode(if (sortMode == "MANUAL") "PRIORITY" else "MANUAL") }) {
                        Text(stringResource(if (sortMode == "MANUAL") R.string.s2_sort_manual else R.string.s2_sort_priority))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { taskEditing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.s2_new_task))
            }
        }
    ) { innerPadding ->
        if (topLevel.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.s2_no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
            ) {
                if (overdue.isNotEmpty()) {
                    item(key = "hdr_overdue") { SectionHeader(stringResource(R.string.s2_sec_overdue), MaterialTheme.colorScheme.error) }
                    items(overdue, key = { "o${it.id}" }) { task -> card(task, false, null) }
                }
                if (todayList.isNotEmpty()) {
                    item(key = "hdr_today") { SectionHeader(stringResource(R.string.s2_sec_today), MaterialTheme.colorScheme.primary) }
                    items(todayList, key = { "t${it.id}" }) { task -> card(task, false, null) }
                }
                item(key = "hdr_loose") { SectionHeader(stringResource(R.string.s2_sec_loose), MaterialTheme.colorScheme.primary) }
                items(loosePending, key = { it.id }) { task ->
                    ReorderableItem(reorderableState, key = task.id) { isDragging ->
                        val source = remember { MutableInteractionSource() }
                        card(
                            task,
                            isDragging,
                            if (sortMode == "MANUAL") Modifier.longPressDraggableHandle(interactionSource = source) else null
                        )
                    }
                }
                if (looseCompleted.isNotEmpty()) {
                    item(key = "hdr_completed") {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { completedExpanded = !completedExpanded }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.s2_completed_count, looseCompleted.size),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(if (completedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                    }
                    if (completedExpanded) {
                        items(looseCompleted, key = { "c${it.id}" }) { task -> card(task, false, null) }
                    }
                }
                if (futureList.isNotEmpty()) {
                    item(key = "hdr_future") { SectionHeader(stringResource(R.string.s2_sec_future), MaterialTheme.colorScheme.primary) }
                    items(futureList, key = { "f${it.id}" }) { task -> card(task, false, null) }
                }
                item(key = "spacer_end") { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }

    if (showDialog) {
        val editing = taskEditing
        TaskDialog(
            taskToEdit = editing,
            existingSubtasks = if (editing != null) subtasksOf(editing.id) else emptyList(),
            onDismiss = { showDialog = false; taskEditing = null },
            onSave = { result -> viewModel.saveTaskFromResult(result) },
            onDelete = { id -> handleDeleteId(id) },
            onToggleSubtask = { viewModel.toggleTaskCompletion(it) },
            onDeleteSubtask = { viewModel.deleteTask(it) }
        )
    }

    parentPrompt?.let { task ->
        AlertDialog(
            onDismissRequest = { parentPrompt = null },
            title = { Text(stringResource(R.string.s2_complete_subtasks_title)) },
            text = { Text(stringResource(R.string.s2_complete_subtasks_msg)) },
            confirmButton = {
                TextButton(onClick = { viewModel.completeWithSubtasks(task); parentPrompt = null }) { Text(stringResource(R.string.s2_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleTaskCompletion(task); parentPrompt = null }) { Text(stringResource(R.string.s2_only_this)) }
            }
        )
    }

    advancePrompt?.let { task ->
        AlertDialog(
            onDismissRequest = { advancePrompt = null },
            title = { Text(stringResource(R.string.pt_advance_title)) },
            text = { Text(stringResource(R.string.pt_advance_msg)) },
            confirmButton = {
                TextButton(onClick = { viewModel.completeAdvanceReplanning(task, todayEpoch); advancePrompt = null }) {
                    Text(stringResource(R.string.pt_advance_reschedule))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleTaskCompletion(task); advancePrompt = null }) {
                    Text(stringResource(R.string.pt_advance_keep))
                }
            }
        )
    }

    subtaskParent?.let { parent ->
        var text by remember(parent.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { subtaskParent = null },
            title = { Text(stringResource(R.string.s2_add_subtask_title)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { viewModel.addSubtask(parent, text); subtaskParent = null }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { subtaskParent = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    pickDateTask?.let { task ->
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        CustomDatePickerDialog(
            state = state,
            onDismiss = { pickDateTask = null },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    handleMove(task, Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay())
                }
                pickDateTask = null
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ProjectTodayRow(
    item: com.example.taskflow.domain.ProjectTodayItem,
    onComplete: () -> Unit,
    onFocus: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.completed, onCheckedChange = { onComplete() })
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    com.example.taskflow.domain.formatHours(item.minutes) + " h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onFocus) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Timer,
                    contentDescription = stringResource(R.string.p2d_focus_action)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(
    task: TaskEntity,
    subtasks: List<TaskEntity>,
    todayEpoch: Long,
    isDragging: Boolean = false,
    dragModifier: Modifier? = null,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveTo: (Long) -> Unit,
    onPickDate: () -> Unit,
    onAddSubtask: () -> Unit,
    onStartFocus: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
        false
    })
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM") }
    val date = task.date
    val dueMinute = task.dueMinuteOfDay
    val dueText = buildString {
        if (date != null && date != todayEpoch) append(LocalDate.ofEpochDay(date).format(dateFormat)).append(" ")
        if (dueMinute != null) append(formatMinute(dueMinute))
    }.trim()
    val original = task.originalDate
    val sinceText = if (original != null && original != date) {
        stringResource(R.string.s2_since, LocalDate.ofEpochDay(original).format(dateFormat))
    } else ""
    val estimateText = task.estimatedMinutes?.let { "~" + durationText(it) } ?: ""
    val repeatText = if (task.recurrenceType != "NONE") stringResource(R.string.s3_task_repeats) else ""
    val priorityColor = when (task.priority) {
        3 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                label = "swipe"
            )
            Box(
                modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(8.dp)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
            Row(
                modifier = Modifier.padding(8.dp).background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
                verticalAlignment = Alignment.Top
            ) {
                if (dragModifier != null) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.s2_reorder),
                        modifier = dragModifier.padding(end = 4.dp).align(Alignment.CenterVertically)
                    )
                }
                if (task.priority > 0) {
                    Box(modifier = Modifier.size(4.dp, 24.dp).background(priorityColor, RoundedCornerShape(2.dp)).align(Alignment.CenterVertically))
                }
                Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() }, modifier = Modifier.align(Alignment.CenterVertically))
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (dueText.isNotEmpty() || sinceText.isNotEmpty() || estimateText.isNotEmpty() || repeatText.isNotEmpty()) {
                        Text(
                            listOf(dueText, sinceText, estimateText, repeatText).filter { it.isNotEmpty() }.joinToString("  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val notes = task.notes
                    if (!notes.isNullOrBlank()) {
                        Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (subtasks.isNotEmpty()) {
                        Text(
                            stringResource(R.string.s2_subtasks_count, subtasks.count { it.isCompleted }, subtasks.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        subtasks.take(3).forEach { sub ->
                            Row(modifier = Modifier.padding(start = 12.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    sub.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onAddSubtask, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.s2_add_subtask))
                }
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!task.isProjectDateLocked()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_move_today)) }, onClick = { menuExpanded = false; onMoveTo(todayEpoch) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_move_tomorrow)) }, onClick = { menuExpanded = false; onMoveTo(todayEpoch + 1) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_move_pick)) }, onClick = { menuExpanded = false; onPickDate() })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.s3_focus_start)) }, onClick = { menuExpanded = false; onStartFocus() })
                    }
                }
            }
        }
    }
}
