package com.example.taskflow.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.entities.TaskReminderEntity
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.domain.isProjectDateLocked
import com.example.taskflow.ui.components.CustomDatePickerDialog
import com.example.taskflow.ui.components.CustomTimePickerDialog
import com.example.taskflow.ui.components.WeekdayToggleRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TaskFormResult(
    val id: Long,
    val title: String,
    val date: Long?,
    val dueMinuteOfDay: Int?,
    val priority: Int,
    val notes: String?,
    val newSubtasks: List<String>,
    val reminders: List<TaskReminderEntity>,
    val estimatedMinutes: Int? = null,
    val recurrenceType: String = "NONE",
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdaysMask: Int = 0
)

@Composable
fun taskReminderLabel(r: TaskReminderEntity): String {
    val offset = r.offsetMinutes
    if (offset != null) {
        return when (offset) {
            0 -> stringResource(R.string.s2_trem_at)
            -540 -> stringResource(R.string.s2_trem_same_day_9)
            240 -> stringResource(R.string.s2_rem_prev_day_20)
            60 -> stringResource(R.string.s2_rem_hour)
            1440 -> stringResource(R.string.s2_rem_1day)
            else -> stringResource(R.string.s2_rem_min, offset)
        }
    }
    val day = r.absEpochDay
    val minute = r.absMinuteOfDay
    if (day != null && minute != null) {
        return stringResource(
            R.string.s2_trem_abs,
            LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            formatMinute(minute)
        )
    }
    return ""
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDialog(
    initialDate: Long? = null,
    taskToEdit: TaskEntity? = null,
    existingSubtasks: List<TaskEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (TaskFormResult) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onToggleSubtask: ((TaskEntity) -> Unit)? = null,
    onDeleteSubtask: ((Long) -> Unit)? = null,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var notes by remember { mutableStateOf(taskToEdit?.notes ?: "") }
    var priority by remember { mutableStateOf(taskToEdit?.priority ?: 0) }
    var selectedDate by remember { mutableStateOf(taskToEdit?.date ?: initialDate) }
    var dueMinute by remember { mutableStateOf(taskToEdit?.dueMinuteOfDay) }
    var estimated by remember { mutableStateOf(taskToEdit?.estimatedMinutes) }
    var recType by remember { mutableStateOf(taskToEdit?.recurrenceType ?: "NONE") }
    var recInterval by remember { mutableStateOf((taskToEdit?.recurrenceInterval ?: 1).toString()) }
    var recMask by remember { mutableStateOf(taskToEdit?.recurrenceWeekdaysMask ?: 0) }
    var focusTotal by remember { mutableStateOf(0) }
    var titleError by remember { mutableStateOf(false) }
    var droppedWarning by remember { mutableStateOf(false) }

    var localSubtasks by remember { mutableStateOf(listOf<String>()) }
    var newSubtaskTitle by remember { mutableStateOf("") }
    val reminders = remember { mutableStateListOf<TaskReminderEntity>() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }
    var showAbsDatePicker by remember { mutableStateOf(false) }
    var showAbsTimePicker by remember { mutableStateOf(false) }
    var absPickedDay by remember { mutableStateOf<Long?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var recExpanded by remember { mutableStateOf(false) }
    val isProject = taskToEdit?.projectKey != null
    val dateLocked = taskToEdit?.isProjectDateLocked() == true

    LaunchedEffect(taskToEdit?.id) {
        if (taskToEdit != null) {
            reminders.clear()
            reminders.addAll(viewModel.loadReminders(taskToEdit.id))
            focusTotal = viewModel.focusMinutes(taskToEdit.id)
        }
    }

    fun dropRelativeReminders() {
        val before = reminders.size
        reminders.removeAll { it.offsetMinutes != null }
        if (reminders.size != before) droppedWarning = true
    }

    fun relativeReminder(offset: Int) = TaskReminderEntity(taskId = 0L, offsetMinutes = offset, absEpochDay = null, absMinuteOfDay = null)

    val recLabel = when (recType) {
        "DAILY" -> stringResource(R.string.s2_rec_daily)
        "WEEKLY" -> stringResource(R.string.s2_rec_weekly)
        "MONTHLY" -> stringResource(R.string.s2_rec_monthly)
        "YEARLY" -> stringResource(R.string.s2_rec_yearly)
        else -> stringResource(R.string.s2_rec_none)
    }
    val unitLabel = when (recType) {
        "DAILY" -> stringResource(R.string.s2_unit_days)
        "WEEKLY" -> stringResource(R.string.s2_unit_weeks)
        "MONTHLY" -> stringResource(R.string.s2_unit_months)
        else -> stringResource(R.string.s2_unit_years)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (taskToEdit == null) R.string.s2_task_new else R.string.s2_task_edit)) },
                        navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
                        actions = {
                            if (taskToEdit != null && onDelete != null && !isProject) {
                                IconButton(onClick = { showDeleteConfirm = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            TextButton(onClick = {
                                if (title.isBlank()) {
                                    titleError = true
                                } else {
                                    onSave(
                                        TaskFormResult(
                                            id = taskToEdit?.id ?: 0L,
                                            title = title.trim(),
                                            date = selectedDate,
                                            dueMinuteOfDay = if (selectedDate != null) dueMinute else null,
                                            priority = priority,
                                            notes = notes.takeIf { it.isNotBlank() },
                                            newSubtasks = localSubtasks,
                                            reminders = reminders.toList(),
                                            estimatedMinutes = estimated,
                                            recurrenceType = recType,
                                            recurrenceInterval = (recInterval.toIntOrNull() ?: 1).coerceAtLeast(1),
                                            recurrenceWeekdaysMask = if (recType == "WEEKLY") recMask else 0
                                        )
                                    )
                                    onDismiss()
                                }
                            }) { Text(stringResource(R.string.save)) }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; titleError = false },
                        label = { Text(stringResource(R.string.s2_task_title_label)) },
                        isError = titleError,
                        readOnly = isProject,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    val d = selectedDate
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s2_task_date) + ": " + (d?.let { LocalDate.ofEpochDay(it).format(dateFormat) } ?: stringResource(R.string.s2_none)))
                        OutlinedButton(onClick = { showDatePicker = true }, enabled = !dateLocked) { Text(stringResource(R.string.change)) }
                    }
                    if (d != null) {
                        val dm = dueMinute
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s2_task_time) + ": " + (if (dm != null) formatMinute(dm) else stringResource(R.string.s2_none)))
                            Row {
                                if (dm != null) {
                                    TextButton(onClick = { dueMinute = null; dropRelativeReminders() }) { Text(stringResource(R.string.s2_remove)) }
                                }
                                OutlinedButton(onClick = { showTimePicker = true }) { Text(stringResource(R.string.change)) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s2_priority), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to R.string.s2_prio_none, 1 to R.string.s2_prio_low,
                            2 to R.string.s2_prio_medium, 3 to R.string.s2_prio_high
                        ).forEach { (value, labelRes) ->
                            FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(stringResource(labelRes)) })
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s3_estimated), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf<Int?>(null, 5, 10, 15, 30, 45, 60, 90, 120).forEach { value ->
                            FilterChip(
                                selected = estimated == value,
                                onClick = { estimated = value },
                                label = { Text(if (value == null) stringResource(R.string.s2_none) else durationText(value)) }
                            )
                        }
                    }
                    if (focusTotal > 0) {
                        Text(
                            stringResource(R.string.s3_focus_time, durationText(focusTotal)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    ExposedDropdownMenuBox(expanded = recExpanded, onExpandedChange = { if (!isProject) recExpanded = it }) {
                        OutlinedTextField(
                            value = recLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.s3_repeat_task)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = recExpanded, onDismissRequest = { recExpanded = false }) {
                            listOf(
                                "NONE" to R.string.s2_rec_none, "DAILY" to R.string.s2_rec_daily,
                                "WEEKLY" to R.string.s2_rec_weekly, "MONTHLY" to R.string.s2_rec_monthly,
                                "YEARLY" to R.string.s2_rec_yearly
                            ).forEach { (value, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = { recType = value; recExpanded = false }
                                )
                            }
                        }
                    }
                    if (recType != "NONE") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = recInterval,
                                onValueChange = { recInterval = it },
                                label = { Text(stringResource(R.string.s2_rec_every)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Text(unitLabel, modifier = Modifier.weight(1f))
                        }
                    }
                    if (recType == "WEEKLY") {
                        Spacer(modifier = Modifier.height(8.dp))
                        WeekdayToggleRow(mask = recMask, onToggle = { bit -> recMask = recMask xor (1 shl bit) })
                        Text(
                            stringResource(R.string.s3_task_weekly_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.s2_event_notes)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.s2_reminders), style = MaterialTheme.typography.titleMedium)
                    if (droppedWarning) {
                        Text(stringResource(R.string.s2_trem_dropped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        reminders.toList().forEach { r ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(taskReminderLabel(r)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.s2_remove),
                                        modifier = Modifier.size(16.dp).clickable { reminders.remove(r) }
                                    )
                                }
                            )
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { showReminderMenu = true }) { Text(stringResource(R.string.s2_add_reminder)) }
                        DropdownMenu(expanded = showReminderMenu, onDismissRequest = { showReminderMenu = false }) {
                            if (selectedDate != null) {
                                val offsets = if (dueMinute != null) listOf(0, 10, 30, 60, 1440) else listOf(-540, 240)
                                offsets.forEach { offset ->
                                    DropdownMenuItem(
                                        text = { Text(taskReminderLabel(relativeReminder(offset))) },
                                        onClick = {
                                            if (reminders.none { it.offsetMinutes == offset }) reminders.add(relativeReminder(offset))
                                            showReminderMenu = false
                                        }
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.s2_trem_pick)) },
                                onClick = { showReminderMenu = false; showAbsDatePicker = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.s2_subtasks), style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newSubtaskTitle,
                            onValueChange = { newSubtaskTitle = it },
                            label = { Text(stringResource(R.string.s2_add_subtask)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (newSubtaskTitle.isNotBlank()) {
                                localSubtasks = localSubtasks + newSubtaskTitle.trim()
                                newSubtaskTitle = ""
                            }
                        }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.s2_add)) }
                    }
                    existingSubtasks.forEach { sub ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = sub.isCompleted, onCheckedChange = { onToggleSubtask?.invoke(sub) })
                            Text(sub.title, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeleteSubtask?.invoke(sub.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    localSubtasks.forEach { local ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(local, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            IconButton(onClick = { localSubtasks = localSubtasks - local }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.s2_remove))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() }
                ?: System.currentTimeMillis()
        )
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                    dropRelativeReminders()
                }
                showDatePicker = false
            },
            onClear = {
                selectedDate = null
                dueMinute = null
                dropRelativeReminders()
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = (dueMinute ?: 720) / 60, initialMinute = (dueMinute ?: 720) % 60)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                dueMinute = state.hour * 60 + state.minute
                dropRelativeReminders()
                showTimePicker = false
            }
        )
    }

    if (showAbsDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showAbsDatePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    absPickedDay = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                    showAbsTimePicker = true
                }
                showAbsDatePicker = false
            }
        )
    }

    if (showAbsTimePicker) {
        val state = rememberTimePickerState(initialHour = 9, initialMinute = 0)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { showAbsTimePicker = false },
            onConfirm = {
                val day = absPickedDay
                if (day != null) {
                    reminders.add(TaskReminderEntity(taskId = 0L, offsetMinutes = null, absEpochDay = day, absMinuteOfDay = state.hour * 60 + state.minute))
                }
                showAbsTimePicker = false
            }
        )
    }

    if (showDeleteConfirm && taskToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.s2_task_delete_title)) },
            text = { Text(stringResource(R.string.s2_task_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke(taskToEdit.id)
                    onDismiss()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
