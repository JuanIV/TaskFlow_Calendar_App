package com.example.taskflow.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventExceptionEntity
import com.example.taskflow.domain.EventColors
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.ui.components.CustomDatePickerDialog
import com.example.taskflow.ui.components.CustomTimePickerDialog
import com.example.taskflow.ui.components.IconPicker
import com.example.taskflow.ui.theme.rememberPalette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventDialog(
    initialDate: LocalDate,
    baseEvent: EventEntity? = null,
    occurrenceToEdit: EventOccurrence? = null,
    initialStartMinute: Int? = null,
    onDismiss: () -> Unit,
    viewModel: EventViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    var title by remember { mutableStateOf(occurrenceToEdit?.title ?: baseEvent?.title ?: "") }
    var startDate by remember { mutableStateOf(occurrenceToEdit?.startDate?.let { LocalDate.ofEpochDay(it) } ?: initialDate) }
    var endDate by remember { mutableStateOf<LocalDate?>(occurrenceToEdit?.endDate?.let { LocalDate.ofEpochDay(it) }) }
    var allDay by remember { mutableStateOf(occurrenceToEdit?.allDay ?: baseEvent?.allDay ?: (initialStartMinute == null)) }
    var startMinute by remember { mutableStateOf(occurrenceToEdit?.startMinute ?: baseEvent?.startMinuteOfDay ?: initialStartMinute ?: 540) }
    var endMinute by remember { mutableStateOf<Int?>(occurrenceToEdit?.endMinute ?: baseEvent?.endMinuteOfDay) }
    var colorArgb by remember { mutableStateOf(occurrenceToEdit?.colorArgb ?: baseEvent?.colorArgb ?: EventColors[0].toArgb()) }
    var notes by remember { mutableStateOf(occurrenceToEdit?.notes ?: baseEvent?.notes ?: "") }
    var location by remember { mutableStateOf(occurrenceToEdit?.location ?: baseEvent?.location ?: "") }
    var importance by remember { mutableStateOf(occurrenceToEdit?.priority ?: baseEvent?.priority ?: "IMPORTANT") }
    var icon by remember { mutableStateOf(occurrenceToEdit?.icon ?: baseEvent?.icon) }
    var alarmMode by remember { mutableStateOf(baseEvent?.alarmMode ?: false) }

    var recType by remember { mutableStateOf(baseEvent?.recurrenceType ?: "NONE") }
    var recInterval by remember { mutableStateOf(baseEvent?.recurrenceInterval ?: 1) }
    var recMask by remember { mutableStateOf(baseEvent?.recurrenceWeekdaysMask ?: 0) }
    var recEndType by remember { mutableStateOf(baseEvent?.recurrenceEndType ?: "NEVER") }
    var recUntil by remember { mutableStateOf<Long?>(baseEvent?.recurrenceUntil) }
    var recCount by remember { mutableStateOf<Int?>(baseEvent?.recurrenceCount) }

    val reminders = remember { mutableStateListOf<Int>() }
    val palette = rememberPalette()

    var titleError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceMenu by remember { mutableStateOf(false) }
    var showRecurrenceDialog by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }

    val errEndBeforeStart = stringResource(R.string.s2_err_end_before_start)
    val remindersCleared = stringResource(R.string.s2_reminders_cleared)

    LaunchedEffect(baseEvent?.id) {
        if (baseEvent != null) {
            reminders.clear()
            reminders.addAll(viewModel.loadReminderOffsets(baseEvent.id))
        }
    }

    fun validate(): Boolean {
        if (title.isBlank()) {
            titleError = true
            return false
        }
        val end = endDate
        val em = endMinute
        if (end != null && end < startDate) {
            dateError = true
            return false
        }
        if (!allDay && em != null && (end == null || end == startDate) && em < startMinute) {
            dateError = true
            return false
        }
        return true
    }

    fun buildEvent(id: Long): EventEntity = EventEntity(
        id = id,
        title = title.trim(),
        date = startDate.toEpochDay(),
        allDay = allDay,
        startMinuteOfDay = if (allDay) null else startMinute,
        colorArgb = colorArgb,
        createdAt = baseEvent?.createdAt ?: System.currentTimeMillis(),
        endDate = endDate?.toEpochDay(),
        endMinuteOfDay = if (allDay) null else endMinute,
        notes = notes.takeIf { it.isNotBlank() },
        location = location.takeIf { it.isNotBlank() },
        recurrenceType = recType,
        recurrenceInterval = recInterval,
        recurrenceWeekdaysMask = recMask,
        recurrenceEndType = recEndType,
        recurrenceUntil = if (recEndType == "UNTIL") recUntil else null,
        recurrenceCount = if (recEndType == "COUNT") recCount else null,
        deletedAt = null,
        priority = importance,
        icon = icon,
        alarmMode = alarmMode
    )

    fun applyPreset(type: String) {
        recType = type
        recInterval = 1
        recEndType = "NEVER"
        recUntil = null
        recCount = null
        recMask = if (type == "WEEKLY") (1 shl (startDate.dayOfWeek.value - 1)) else 0
    }

    fun onSaveClicked() {
        if (!validate()) return
        if (baseEvent != null && baseEvent.recurrenceType != "NONE") {
            showActionDialog = true
        } else {
            viewModel.saveEvent(buildEvent(baseEvent?.id ?: 0L), reminders.toList())
            onDismiss()
        }
    }

    val weekdayBit = 1 shl (startDate.dayOfWeek.value - 1)
    val presetLabel = when {
        recType == "NONE" -> stringResource(R.string.s2_rec_none)
        recEndType == "NEVER" && recInterval == 1 && recType == "DAILY" -> stringResource(R.string.s2_rec_daily)
        recEndType == "NEVER" && recInterval == 1 && recType == "WEEKLY" && recMask == weekdayBit -> stringResource(R.string.s2_rec_weekly)
        recEndType == "NEVER" && recInterval == 1 && recType == "MONTHLY_DAY" -> stringResource(R.string.s2_rec_monthly)
        recEndType == "NEVER" && recInterval == 1 && recType == "YEARLY" -> stringResource(R.string.s2_rec_yearly)
        else -> stringResource(R.string.s2_rec_custom)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (baseEvent == null) R.string.s2_event_new else R.string.s2_event_edit)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                            }
                        },
                        actions = {
                            TextButton(onClick = { onSaveClicked() }) { Text(stringResource(R.string.save)) }
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
                        label = { Text(stringResource(R.string.s2_event_title_label)) },
                        isError = titleError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    if (dateError) {
                        Text(errEndBeforeStart, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s2_event_start_date) + ": " + startDate.format(dateFormat))
                        OutlinedButton(onClick = { showStartPicker = true }) { Text(stringResource(R.string.change)) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s2_event_end_date) + ": " + (endDate?.format(dateFormat) ?: stringResource(R.string.s2_none)))
                        OutlinedButton(onClick = { showEndPicker = true }) { Text(stringResource(R.string.change)) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s2_all_day))
                        Switch(
                            checked = allDay,
                            onCheckedChange = { checked ->
                                allDay = checked
                                if (reminders.isNotEmpty()) {
                                    reminders.clear()
                                    scope.launch { snackbarHostState.showSnackbar(remindersCleared) }
                                }
                            }
                        )
                    }

                    if (!allDay) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s2_event_start_time) + ": " + formatMinute(startMinute))
                            OutlinedButton(onClick = { showStartTimePicker = true }) { Text(stringResource(R.string.change)) }
                        }
                        val em = endMinute
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s2_event_end_time) + ": " + (if (em != null) formatMinute(em) else stringResource(R.string.s2_none)))
                            Row {
                                if (em != null) {
                                    TextButton(onClick = { endMinute = null }) { Text(stringResource(R.string.s2_remove)) }
                                }
                                OutlinedButton(onClick = { showEndTimePicker = true }) { Text(stringResource(R.string.change)) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    ExposedDropdownMenuBox(expanded = showRecurrenceMenu, onExpandedChange = { showRecurrenceMenu = it }) {
                        OutlinedTextField(
                            value = presetLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.s2_repeat)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRecurrenceMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = showRecurrenceMenu, onDismissRequest = { showRecurrenceMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_none)) }, onClick = { applyPreset("NONE"); showRecurrenceMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_daily)) }, onClick = { applyPreset("DAILY"); showRecurrenceMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_weekly)) }, onClick = { applyPreset("WEEKLY"); showRecurrenceMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_monthly)) }, onClick = { applyPreset("MONTHLY_DAY"); showRecurrenceMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_yearly)) }, onClick = { applyPreset("YEARLY"); showRecurrenceMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_custom)) }, onClick = { showRecurrenceMenu = false; showRecurrenceDialog = true })
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.s2_reminders), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        reminders.toList().forEach { offset ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(reminderLabel(offset, allDay)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.s2_remove),
                                        modifier = Modifier.size(16.dp).clickable { reminders.remove(offset) }
                                    )
                                }
                            )
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { showReminderMenu = true }) { Text(stringResource(R.string.s2_add_reminder)) }
                        DropdownMenu(expanded = showReminderMenu, onDismissRequest = { showReminderMenu = false }) {
                            reminderPresets(allDay).forEach { offset ->
                                DropdownMenuItem(
                                    text = { Text(reminderLabel(offset, allDay)) },
                                    onClick = {
                                        if (!reminders.contains(offset)) reminders.add(offset)
                                        showReminderMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s3_alarm_mode), modifier = Modifier.weight(1f))
                        Switch(checked = alarmMode, onCheckedChange = { alarmMode = it })
                    }
                    Text(
                        stringResource(R.string.s3_alarm_mode_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.s3_icon), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    IconPicker(selected = icon, onSelect = { icon = it })

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.s2_importance), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = importance == "IMPORTANT", onClick = { importance = "IMPORTANT" }, label = { Text(stringResource(R.string.s2_importance_important)) })
                        FilterChip(selected = importance == "SECONDARY", onClick = { importance = "SECONDARY" }, label = { Text(stringResource(R.string.s2_importance_secondary)) })
                    }
                    Text(stringResource(R.string.s2_importance_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = location, onValueChange = { location = it },
                        label = { Text(stringResource(R.string.s2_event_location)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.s2_event_notes)) },
                        minLines = 3, modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.s2_color), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(palette) { color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { colorArgb = color.toArgb() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (color.toArgb() == colorArgb) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecurrenceDialog) {
        RecurrenceDialog(
            initialStartDate = startDate.toEpochDay(),
            initialInterval = recInterval,
            initialType = recType,
            initialMask = recMask,
            initialEndType = recEndType,
            initialUntil = recUntil,
            initialCount = recCount,
            onDismiss = { showRecurrenceDialog = false },
            onSave = { interval, type, mask, endType, until, count, shifted ->
                val delta = shifted - startDate.toEpochDay()
                endDate = endDate?.plusDays(delta)
                startDate = LocalDate.ofEpochDay(shifted)
                recInterval = interval
                recType = type
                recMask = mask
                recEndType = endType
                recUntil = if (endType == "UNTIL") until else null
                recCount = if (endType == "COUNT") count else null
                showRecurrenceDialog = false
            }
        )
    }

    if (showActionDialog && baseEvent != null) {
        RecurringActionDialog(
            isDelete = false,
            onDismiss = { showActionDialog = false },
            onActionSelected = { choice ->
                val origDate = occurrenceToEdit?.originalDate ?: baseEvent.date
                val occStart = occurrenceToEdit?.startDate ?: baseEvent.date
                when (choice) {
                    "THIS_EVENT" -> viewModel.saveException(
                        EventExceptionEntity(
                            eventId = baseEvent.id,
                            originalDate = origDate,
                            isDeleted = false,
                            overrideTitle = title.trim(),
                            overrideDate = startDate.toEpochDay(),
                            overrideEndDate = endDate?.toEpochDay(),
                            overrideAllDay = allDay,
                            overrideStartMinute = if (allDay) null else startMinute,
                            overrideEndMinute = if (allDay) null else endMinute,
                            overrideColorArgb = colorArgb,
                            overrideNotes = notes.takeIf { it.isNotBlank() },
                            overrideLocation = location.takeIf { it.isNotBlank() },
                            overrideImportance = importance
                        )
                    )
                    "THIS_AND_FOLLOWING" -> {
                        if (origDate == baseEvent.date) {
                            viewModel.updateSeries(baseEvent, occStart, buildEvent(baseEvent.id), reminders.toList())
                        } else {
                            viewModel.splitSeries(baseEvent, origDate, buildEvent(0L), reminders.toList())
                        }
                    }
                    else -> viewModel.updateSeries(baseEvent, occStart, buildEvent(baseEvent.id), reminders.toList())
                }
                showActionDialog = false
                onDismiss()
            }
        )
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    val newDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                    val delta = newDate.toEpochDay() - startDate.toEpochDay()
                    endDate = endDate?.plusDays(delta)
                    startDate = newDate
                }
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (endDate ?: startDate).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    endDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                }
                showEndPicker = false
            },
            onClear = { endDate = null; showEndPicker = false }
        )
    }

    if (showStartTimePicker) {
        val state = rememberTimePickerState(initialHour = startMinute / 60, initialMinute = startMinute % 60)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { startMinute = state.hour * 60 + state.minute; showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        val current = endMinute ?: ((startMinute + 60) % 1440)
        val state = rememberTimePickerState(initialHour = current / 60, initialMinute = current % 60)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { endMinute = state.hour * 60 + state.minute; showEndTimePicker = false }
        )
    }
}
