package com.example.taskflow.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.taskflow.R
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.domain.EventColors
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.domain.isValidLogicalWindow
import com.example.taskflow.ui.components.CustomDatePickerDialog
import com.example.taskflow.ui.components.CustomTimePickerDialog
import com.example.taskflow.ui.components.IconPicker
import com.example.taskflow.ui.components.WeekdayToggleRow
import com.example.taskflow.ui.theme.rememberPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun LabeledRow(label: String, value: String, onChange: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Row {
            if (onClear != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.s2_remove)) }
            }
            OutlinedButton(onClick = onChange) { Text(stringResource(R.string.change)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDialog(
    initialAnchorDate: Long = 0L,
    dayStartHour: Int = 0,
    routineToEdit: RoutineEntity? = null,
    onDismiss: () -> Unit,
    onSave: (RoutineEntity) -> Unit
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val palette = rememberPalette()
    var name by remember { mutableStateOf(routineToEdit?.name ?: "") }
    var colorArgb by remember { mutableStateOf(routineToEdit?.colorArgb ?: EventColors[0].toArgb()) }
    var isActive by remember { mutableStateOf(routineToEdit?.isActive ?: true) }
    var recurrenceType by remember { mutableStateOf(routineToEdit?.recurrenceType ?: "WEEKDAYS") }
    var weekdaysMask by remember {
        mutableStateOf(routineToEdit?.weekdaysMask ?: (1 shl (LocalDate.ofEpochDay(initialAnchorDate).dayOfWeek.value - 1)))
    }
    var intervalStr by remember { mutableStateOf((routineToEdit?.intervalDays ?: 1).toString()) }
    var anchorDate by remember { mutableStateOf(routineToEdit?.anchorDate ?: initialAnchorDate) }
    var startMinute by remember { mutableStateOf(routineToEdit?.startMinuteOfDay ?: 480) }
    var endMinute by remember { mutableStateOf<Int?>(routineToEdit?.endMinuteOfDay) }
    var endWarning by remember { mutableStateOf<Int?>(routineToEdit?.endWarningMinutes) }
    var startDate by remember { mutableStateOf<Long?>(routineToEdit?.startDate) }
    var endDate by remember { mutableStateOf<Long?>(routineToEdit?.endDate) }
    var graceDays by remember { mutableStateOf(routineToEdit?.graceDaysPerMonth ?: 0) }
    var icon by remember { mutableStateOf(routineToEdit?.icon) }
    var sequential by remember { mutableStateOf(routineToEdit?.sequentialMode ?: false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var showAnchorPicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val errName = stringResource(R.string.s2_err_name)
    val errDays = stringResource(R.string.s2_err_days)
    val errInterval = stringResource(R.string.s2_err_interval)
    val errWindow = stringResource(R.string.s2_err_window)
    val errDates = stringResource(R.string.s2_err_dates)
    val none = stringResource(R.string.s2_none)

    fun trySave() {
        val interval = intervalStr.toIntOrNull() ?: 0
        val end = endMinute
        val sd = startDate
        val ed = endDate
        when {
            name.isBlank() -> errorText = errName
            recurrenceType == "WEEKDAYS" && weekdaysMask == 0 -> errorText = errDays
            recurrenceType == "EVERY_N_DAYS" && interval < 1 -> errorText = errInterval
            end != null && !isValidLogicalWindow(startMinute, end, dayStartHour) -> errorText = errWindow
            sd != null && ed != null && ed < sd -> errorText = errDates
            else -> {
                val finalMask = if (recurrenceType == "WEEKDAYS") weekdaysMask else 0
                val finalInterval = interval.coerceAtLeast(1)
                val finalWarning = if (end != null) endWarning else null
                val base = routineToEdit
                val entity = base?.copy(
                    name = name.trim(),
                    colorArgb = colorArgb,
                    recurrenceType = recurrenceType,
                    weekdaysMask = finalMask,
                    intervalDays = finalInterval,
                    anchorDate = anchorDate,
                    startMinuteOfDay = startMinute,
                    isActive = isActive,
                    endMinuteOfDay = end,
                    endWarningMinutes = finalWarning,
                    startDate = sd,
                    endDate = ed,
                    graceDaysPerMonth = graceDays,
                    icon = icon,
                    sequentialMode = sequential
                ) ?: RoutineEntity(
                    name = name.trim(),
                    colorArgb = colorArgb,
                    recurrenceType = recurrenceType,
                    weekdaysMask = finalMask,
                    intervalDays = finalInterval,
                    anchorDate = anchorDate,
                    startMinuteOfDay = startMinute,
                    isActive = isActive,
                    createdAt = System.currentTimeMillis(),
                    endMinuteOfDay = end,
                    endWarningMinutes = finalWarning,
                    startDate = sd,
                    endDate = ed,
                    graceDaysPerMonth = graceDays,
                    icon = icon,
                    sequentialMode = sequential
                )
                onSave(entity)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (routineToEdit == null) R.string.s2_routine_new else R.string.s2_routine_edit)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel)) }
                        },
                        actions = { TextButton(onClick = { trySave() }) { Text(stringResource(R.string.save)) } }
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
                    val error = errorText
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorText = null },
                        label = { Text(stringResource(R.string.s2_routine_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s2_color), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(palette) { color ->
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(color).clickable { colorArgb = color.toArgb() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (color.toArgb() == colorArgb) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s2_routine_active))
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = recurrenceType == "WEEKDAYS", onClick = { recurrenceType = "WEEKDAYS"; errorText = null }, label = { Text(stringResource(R.string.s2_rtype_weekdays)) })
                        FilterChip(selected = recurrenceType == "EVERY_N_DAYS", onClick = { recurrenceType = "EVERY_N_DAYS"; errorText = null }, label = { Text(stringResource(R.string.s2_rtype_interval)) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (recurrenceType == "WEEKDAYS") {
                        WeekdayToggleRow(mask = weekdaysMask, onToggle = { bit -> weekdaysMask = weekdaysMask xor (1 shl bit); errorText = null })
                    } else {
                        OutlinedTextField(
                            value = intervalStr,
                            onValueChange = { intervalStr = it; errorText = null },
                            label = { Text(stringResource(R.string.s2_routine_every_x)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LabeledRow(
                            label = stringResource(R.string.s2_routine_anchor),
                            value = LocalDate.ofEpochDay(anchorDate).format(dateFormat),
                            onChange = { showAnchorPicker = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    LabeledRow(
                        label = stringResource(R.string.s2_routine_start_time),
                        value = formatMinute(startMinute),
                        onChange = { showStartTimePicker = true }
                    )
                    val em = endMinute
                    LabeledRow(
                        label = stringResource(R.string.s2_routine_end_time),
                        value = if (em != null) formatMinute(em) else none,
                        onChange = { showEndTimePicker = true },
                        onClear = if (em != null) ({ endMinute = null; endWarning = null }) else null
                    )
                    if (em != null) {
                        Text(stringResource(R.string.s2_routine_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = endWarning == null, onClick = { endWarning = null }, label = { Text(stringResource(R.string.s2_routine_no_warning)) })
                            listOf(15, 30, 60).forEach { minutes ->
                                FilterChip(
                                    selected = endWarning == minutes,
                                    onClick = { endWarning = minutes },
                                    label = { Text(stringResource(R.string.s2_routine_warning_min, minutes)) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    val sd = startDate
                    LabeledRow(
                        label = stringResource(R.string.s2_routine_start_date),
                        value = if (sd != null) LocalDate.ofEpochDay(sd).format(dateFormat) else none,
                        onChange = { showStartDatePicker = true },
                        onClear = if (sd != null) ({ startDate = null }) else null
                    )
                    val ed = endDate
                    LabeledRow(
                        label = stringResource(R.string.s2_routine_end_date),
                        value = if (ed != null) LocalDate.ofEpochDay(ed).format(dateFormat) else none,
                        onChange = { showEndDatePicker = true },
                        onClear = if (ed != null) ({ endDate = null }) else null
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s3_routine_sequential), modifier = Modifier.weight(1f))
                        Switch(checked = sequential, onCheckedChange = { sequential = it })
                    }
                    Text(
                        stringResource(R.string.s3_routine_sequential_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s3_icon), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    IconPicker(selected = icon, onSelect = { icon = it })
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.s2_routine_grace), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..5).forEach { d ->
                            FilterChip(selected = graceDays == d, onClick = { graceDays = d }, label = { Text("$d") })
                        }
                    }
                }
            }
        }
    }

    if (showStartTimePicker) {
        val state = rememberTimePickerState(initialHour = startMinute / 60, initialMinute = startMinute % 60)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { startMinute = state.hour * 60 + state.minute; errorText = null; showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        val current = endMinute ?: 1260
        val state = rememberTimePickerState(initialHour = current / 60, initialMinute = current % 60)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { endMinute = state.hour * 60 + state.minute; errorText = null; showEndTimePicker = false }
        )
    }
    if (showAnchorPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(anchorDate).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showAnchorPicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    anchorDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                }
                showAnchorPicker = false
            }
        )
    }
    if (showStartDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (startDate ?: anchorDate).let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() }
        )
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showStartDatePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                    errorText = null
                }
                showStartDatePicker = false
            }
        )
    }
    if (showEndDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (endDate ?: startDate ?: anchorDate).let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() }
        )
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showEndDatePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    endDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                    errorText = null
                }
                showEndDatePicker = false
            }
        )
    }
}
