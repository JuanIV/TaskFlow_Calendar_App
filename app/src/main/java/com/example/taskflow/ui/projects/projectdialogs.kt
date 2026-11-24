package com.example.taskflow.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.taskflow.R
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.domain.EventColors
import com.example.taskflow.domain.NodeDraft
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.ProjectFormData
import com.example.taskflow.domain.cleanNotes
import com.example.taskflow.domain.epochDayToPickerMillis
import com.example.taskflow.domain.formatHours
import com.example.taskflow.domain.parseHoursToMinutes
import com.example.taskflow.domain.pickerMillisToEpochDay
import com.example.taskflow.domain.validateNodeDraft
import com.example.taskflow.domain.validateProject
import com.example.taskflow.ui.components.CustomDatePickerDialog
import com.example.taskflow.ui.components.IconPicker
import com.example.taskflow.ui.components.WeekdayToggleRow
import com.example.taskflow.ui.theme.rememberPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Texto de error para un resultado de operacion. null si es OK.
@Composable
fun opResultText(result: OpResult): String? = when (result) {
    OpResult.OK -> null
    OpResult.EMPTY_NAME -> stringResource(R.string.p1b_err_name)
    OpResult.INVALID_CAPACITY -> stringResource(R.string.p1b_err_capacity)
    OpResult.INVALID_DAYS -> stringResource(R.string.p1b_err_days)
    OpResult.INVALID_DEADLINE -> stringResource(R.string.p1b_err_deadline)
    OpResult.EMPTY_TITLE -> stringResource(R.string.p1b_err_title)
    OpResult.INVALID_MINUTES -> stringResource(R.string.p1b_err_minutes)
    OpResult.PARENT_IS_MILESTONE -> stringResource(R.string.p1b_err_parent_milestone)
    OpResult.PARENT_HAS_PROGRESS -> stringResource(R.string.p1b_err_parent_progress)
    OpResult.CYCLE -> stringResource(R.string.p1b_err_cycle)
    OpResult.NOT_FOUND -> stringResource(R.string.p1b_err_not_found)
    OpResult.IS_GROUP -> stringResource(R.string.p1b_err_is_group)
    OpResult.INVALID_STATUS -> stringResource(R.string.p1b_err_status)
    OpResult.NEEDS_CONFIRMATION -> stringResource(R.string.p1b_err_needs_confirm)
}

private fun dayText(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@Composable
fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    val palette = rememberPalette()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(palette) { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(color.toArgb()) },
                contentAlignment = Alignment.Center
            ) {
                if (color.toArgb() == selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onChange: () -> Unit, onClear: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label + ": " + value)
        Row {
            if (onClear != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.p1b_remove)) }
            }
            OutlinedButton(onClick = onChange) { Text(stringResource(R.string.p1b_change)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFormDialog(
    existing: ProjectEntity?,
    existingExcludedDays: List<Long>,
    todayEpoch: Long,
    onDismiss: () -> Unit,
    onSave: (ProjectFormData) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var colorArgb by remember { mutableStateOf(existing?.colorArgb ?: EventColors[0].toArgb()) }
    var icon by remember { mutableStateOf(existing?.icon) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: todayEpoch) }
    var deadline by remember { mutableStateOf(existing?.deadlineDate) }
    var hoursText by remember { mutableStateOf(formatHours(existing?.dailyCapacityMinutes ?: 240)) }
    var mask by remember { mutableStateOf(existing?.workingDaysMask ?: 31) }
    var excluded by remember { mutableStateOf(existingExcludedDays.sorted()) }
    var showStart by remember { mutableStateOf(false) }
    var showDeadline by remember { mutableStateOf(false) }
    var showExclude by remember { mutableStateOf(false) }

    val capacity = parseHoursToMinutes(hoursText) ?: -1
    val error = validateProject(name, capacity, mask, startDate, deadline)
    val errorText = opResultText(error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.p1b_project_new else R.string.p1b_project_edit))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.p1b_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.p1b_field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.p1b_color), style = MaterialTheme.typography.titleSmall)
                ColorRow(selected = colorArgb, onSelect = { colorArgb = it })
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.p1b_icon), style = MaterialTheme.typography.titleSmall)
                IconPicker(selected = icon, onSelect = { icon = it })
                Spacer(modifier = Modifier.height(8.dp))
                DateRow(
                    label = stringResource(R.string.p1b_field_start),
                    value = dayText(startDate),
                    onChange = { showStart = true },
                    onClear = null
                )
                DateRow(
                    label = stringResource(R.string.p1b_field_deadline),
                    value = deadline?.let { dayText(it) } ?: stringResource(R.string.p1b_none_date),
                    onChange = { showDeadline = true },
                    onClear = if (deadline != null) ({ deadline = null }) else null
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text(stringResource(R.string.p1b_field_capacity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.p1b_field_working_days), style = MaterialTheme.typography.titleSmall)
                WeekdayToggleRow(mask = mask, onToggle = { bit -> mask = mask xor (1 shl bit) })
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.p1b_field_excluded_days), style = MaterialTheme.typography.titleSmall)
                excluded.forEach { day ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dayText(day), modifier = Modifier.weight(1f))
                        TextButton(onClick = { excluded = excluded - day }) {
                            Text(stringResource(R.string.p1b_remove))
                        }
                    }
                }
                OutlinedButton(onClick = { showExclude = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.p1b_add_excluded_day))
                }
                if (errorText != null) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == OpResult.OK,
                onClick = {
                    onSave(
                        ProjectFormData(
                            name = name.trim(),
                            description = description.trim().takeIf { it.isNotEmpty() },
                            colorArgb = colorArgb,
                            icon = icon,
                            startDate = startDate,
                            deadlineDate = deadline,
                            capacityMinutes = capacity,
                            workingDaysMask = mask,
                            excludedDays = excluded
                        )
                    )
                }
            ) { Text(stringResource(R.string.p1b_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p1b_cancel)) } }
    )

    if (showStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDayToPickerMillis(startDate))
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showStart = false },
            onConfirm = {
                state.selectedDateMillis?.let { startDate = pickerMillisToEpochDay(it) }
                showStart = false
            }
        )
    }
    if (showDeadline) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDayToPickerMillis(deadline ?: startDate))
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showDeadline = false },
            onConfirm = {
                state.selectedDateMillis?.let { deadline = pickerMillisToEpochDay(it) }
                showDeadline = false
            }
        )
    }
    if (showExclude) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDayToPickerMillis(startDate))
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showExclude = false },
            onConfirm = {
                state.selectedDateMillis?.let { ms ->
                    val day = pickerMillisToEpochDay(ms)
                    excluded = (excluded + day).distinct().sorted()
                }
                showExclude = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeFormDialog(
    existing: ProjectNodeEntity?,
    isGroup: Boolean,
    onDismiss: () -> Unit,
    onSave: (NodeDraft) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var hoursText by remember { mutableStateOf(formatHours(existing?.estimatedMinutes ?: 60)) }
    var milestone by remember { mutableStateOf(existing?.isMilestone ?: false) }
    var priority by remember { mutableStateOf(existing?.priority ?: 0) }
    var colorArgb by remember { mutableStateOf(existing?.colorArgb ?: EventColors[0].toArgb()) }
    var notBefore by remember { mutableStateOf(existing?.notBeforeDate) }
    var showNotBefore by remember { mutableStateOf(false) }

    val minutes = parseHoursToMinutes(hoursText) ?: -1
    val draft = NodeDraft(
        title = title.trim(),
        notes = cleanNotes(notes),
        estimatedMinutes = if (milestone) 0 else minutes,
        isMilestone = milestone && !isGroup,
        priority = priority,
        colorArgb = colorArgb,
        notBeforeDate = notBefore
    )
    val error = validateNodeDraft(draft, !isGroup)
    val errorText = opResultText(error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.p1b_node_new else R.string.p1b_node_edit)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.p1b_node_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.p1b_node_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isGroup) {
                    Text(stringResource(R.string.p1b_node_is_group), style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it },
                        label = { Text(stringResource(R.string.p1b_node_hours)) },
                        enabled = !milestone,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.p1b_node_milestone))
                        Switch(checked = milestone, onCheckedChange = { milestone = it })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.p1b_node_priority), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to R.string.p1b_prio_none,
                        1 to R.string.p1b_prio_low,
                        2 to R.string.p1b_prio_medium,
                        3 to R.string.p1b_prio_high
                    ).forEach { (value, labelRes) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.p1b_color), style = MaterialTheme.typography.titleSmall)
                ColorRow(selected = colorArgb, onSelect = { colorArgb = it })
                Spacer(modifier = Modifier.height(8.dp))
                DateRow(
                    label = stringResource(R.string.p1b_node_not_before),
                    value = notBefore?.let { dayText(it) } ?: stringResource(R.string.p1b_none_date),
                    onChange = { showNotBefore = true },
                    onClear = if (notBefore != null) ({ notBefore = null }) else null
                )
                if (errorText != null) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == OpResult.OK, onClick = { onSave(draft) }) {
                Text(stringResource(R.string.p1b_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p1b_cancel)) } }
    )

    if (showNotBefore) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDayToPickerMillis(notBefore ?: LocalDate.now().toEpochDay()))
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showNotBefore = false },
            onConfirm = {
                state.selectedDateMillis?.let { notBefore = pickerMillisToEpochDay(it) }
                showNotBefore = false
            }
        )
    }
}

@Composable
fun MoveNodeDialog(
    targets: List<ProjectNodeEntity>,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.p1b_move_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.p1b_move_root))
                }
                targets.forEach { target ->
                    TextButton(onClick = { onSelect(target.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(target.title)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p1b_cancel)) } }
    )
}

@Composable
fun ConfirmDeleteDialog(
    withProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.p1b_delete_title)) },
        text = { Text(stringResource(if (withProgress) R.string.p1b_delete_progress_msg else R.string.p1b_delete_msg)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.p1b_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p1b_cancel)) } }
    )
}
