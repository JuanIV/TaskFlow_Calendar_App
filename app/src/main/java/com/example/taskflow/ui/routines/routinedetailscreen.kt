package com.example.taskflow.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.domain.canToggleInSequence
import com.example.taskflow.domain.isRoutinePausedOn
import com.example.taskflow.domain.orderForSequence
import com.example.taskflow.domain.routineAppliesOn
import com.example.taskflow.domain.taskAppliesOn
import com.example.taskflow.ui.components.CustomDatePickerDialog
import com.example.taskflow.ui.components.WeekdayToggleRow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun RoutineTaskDialog(
    existing: RoutineTaskEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, Int?) -> Unit
) {
    var text by remember { mutableStateOf(existing?.title ?: "") }
    var optional by remember { mutableStateOf(existing?.isOptional ?: false) }
    var mask by remember { mutableStateOf(existing?.weekdaysMask ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.s2_routine_add_task else R.string.s2_routine_edit_task)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.s2_routine_task_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = optional, onCheckedChange = { optional = it })
                    Text(stringResource(R.string.s2_routine_optional))
                }
                Text(stringResource(R.string.s2_routine_only_days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                WeekdayToggleRow(mask = mask, onToggle = { bit -> mask = mask xor (1 shl bit) })
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text, optional, if (mask == 0) null else mask) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RoutineDetailScreen(
    viewModel: RoutineDetailViewModel = hiltViewModel(),
    routinesViewModel: RoutinesViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val routine by viewModel.routine.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val completions by viewModel.completionsToday.collectAsState()
    val todayEpoch by viewModel.todayEpoch.collectAsState()
    val dayStartHour by viewModel.dayStartHour.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var showEdit by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteRoutine by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showPausePicker by remember { mutableStateOf(false) }
    var taskDialogOpen by remember { mutableStateOf(false) }
    var taskBeingEdited by remember { mutableStateOf<RoutineTaskEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<RoutineTaskEntity?>(null) }

    val deletedMsg = stringResource(R.string.s2_task_deleted)
    val templateSavedMsg = stringResource(R.string.s2_template_saved)
    val undoLabel = stringResource(R.string.s2_undo)

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyColumnState(lazyListState) { from, to ->
        val fromIdx = tasks.indexOfFirst { it.id == (from.key as? Long) }
        val toIdx = tasks.indexOfFirst { it.id == (to.key as? Long) }
        if (fromIdx >= 0 && toIdx >= 0) {
            val mutable = tasks.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            viewModel.reorderTasks(mutable)
        }
    }

    val r = routine
    if (r == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.s2_routine_loading))
        }
        return
    }

    val copyName = stringResource(R.string.s2_routine_copy_suffix, r.name)
    val isPaused = isRoutinePausedOn(r, todayEpoch)
    val appliesToday = routineAppliesOn(r, todayEpoch)
    val doneIds = completions.map { it.routineTaskId }.toSet()
    val seqOrdered = orderForSequence(tasks.filter { taskAppliesOn(it, todayEpoch) })
    val pausedUntil = r.pausedUntilEpochDay
    val statusText = when {
        isPaused && pausedUntil != null -> stringResource(R.string.s2_routine_paused_until, LocalDate.ofEpochDay(pausedUntil).format(dateFormat))
        isPaused -> stringResource(R.string.s2_routine_paused)
        appliesToday -> stringResource(R.string.s2_routine_applies_today)
        else -> stringResource(R.string.s2_routine_not_today)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(r.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.s2_edit)) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_menu)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (r.pausedFromEpochDay != null) R.string.s2_routine_resume else R.string.s2_routine_pause)) },
                                onClick = {
                                    menuExpanded = false
                                    if (r.pausedFromEpochDay != null) viewModel.setPauseState(null, null) else showPauseDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.s2_routine_duplicate)) },
                                onClick = { menuExpanded = false; viewModel.duplicate(false, copyName); onBack() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.s2_routine_save_template)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.duplicate(true, r.name)
                                    scope.launch { snackbarHostState.showSnackbar(templateSavedMsg) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; showDeleteRoutine = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { taskBeingEdited = null; taskDialogOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.s2_routine_add_task))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            Text(
                statusText,
                fontWeight = FontWeight.Bold,
                color = if (isPaused) MaterialTheme.colorScheme.error else if (appliesToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.s2_routine_tasks), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                items(tasks, key = { it.id }) { task ->
                    ReorderableItem(reorderableState, key = task.id) { isDragging ->
                        val source = remember { MutableInteractionSource() }
                        val isDone = completions.any { it.routineTaskId == task.id }
                        val canCheck = appliesToday && taskAppliesOn(task, todayEpoch)
                        val mask = task.weekdaysMask
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.s2_reorder),
                                modifier = Modifier.padding(end = 8.dp).longPressDraggableHandle(interactionSource = source)
                            )
                            if (canCheck) {
                                Checkbox(
                                    checked = isDone,
                                    onCheckedChange = { viewModel.toggleTaskCompletion(task.id, isDone) },
                                    enabled = canToggleInSequence(r.sequentialMode, seqOrdered, doneIds, task.id)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (canCheck && isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (appliesToday && !canCheck) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                                )
                                if (task.isOptional) {
                                    Text(stringResource(R.string.s2_routine_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (mask != null && mask != 0) {
                                    Text(
                                        stringResource(R.string.s2_routine_only_days_label, weekdayLetters(mask)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { taskBeingEdited = task; taskDialogOpen = true }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.s2_edit))
                            }
                            IconButton(onClick = { taskToDelete = task }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item(key = "spacer_end") { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }

    if (showEdit) {
        RoutineDialog(
            initialAnchorDate = todayEpoch,
            dayStartHour = dayStartHour,
            routineToEdit = r,
            onDismiss = { showEdit = false },
            onSave = { updated -> routinesViewModel.saveRoutine(updated) { showEdit = false } }
        )
    }

    if (taskDialogOpen) {
        RoutineTaskDialog(
            existing = taskBeingEdited,
            onDismiss = { taskDialogOpen = false; taskBeingEdited = null },
            onSave = { title, optional, mask ->
                viewModel.saveTask(taskBeingEdited, title, optional, mask)
                taskDialogOpen = false
                taskBeingEdited = null
            }
        )
    }

    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text(stringResource(R.string.s2_task_delete_title)) },
            text = { Text(stringResource(R.string.s2_routine_task_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    taskToDelete = null
                    viewModel.deleteTask(task)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(message = deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreTask(task)
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showDeleteRoutine) {
        AlertDialog(
            onDismissRequest = { showDeleteRoutine = false },
            title = { Text(stringResource(R.string.s2_routine_delete_title)) },
            text = { Text(stringResource(R.string.s2_routine_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteRoutine = false
                    routinesViewModel.deleteRoutine(r.id)
                    onBack()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteRoutine = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showPauseDialog) {
        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            title = { Text(stringResource(R.string.s2_routine_pause_title)) },
            text = {
                Column {
                    TextButton(onClick = { showPauseDialog = false; showPausePicker = true }) {
                        Text(stringResource(R.string.s2_routine_pause_until))
                    }
                    TextButton(onClick = { showPauseDialog = false; viewModel.setPauseState(todayEpoch, null) }) {
                        Text(stringResource(R.string.s2_routine_pause_forever))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPauseDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showPausePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showPausePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    val until = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                    viewModel.setPauseState(todayEpoch, maxOf(until, todayEpoch))
                }
                showPausePicker = false
            }
        )
    }
}
