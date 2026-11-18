package com.example.taskflow.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.taskflow.R
import com.example.taskflow.ui.components.CustomDatePickerDialog
import com.example.taskflow.ui.components.WeekdayToggleRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceDialog(
    initialStartDate: Long,
    initialInterval: Int,
    initialType: String,
    initialMask: Int,
    initialEndType: String,
    initialUntil: Long?,
    initialCount: Int?,
    onDismiss: () -> Unit,
    onSave: (interval: Int, type: String, mask: Int, endType: String, until: Long?, count: Int?, shiftedStart: Long) -> Unit
) {
    var intervalStr by remember { mutableStateOf(initialInterval.toString()) }
    var unit by remember {
        mutableStateOf(
            when (initialType) {
                "MONTHLY_DAY", "MONTHLY_NTH_WEEKDAY" -> "MONTHLY"
                "NONE" -> "DAILY"
                else -> initialType
            }
        )
    }
    var monthlyMode by remember { mutableStateOf(if (initialType == "MONTHLY_NTH_WEEKDAY") "MONTHLY_NTH_WEEKDAY" else "MONTHLY_DAY") }
    var mask by remember {
        mutableStateOf(if (initialMask != 0) initialMask else 1 shl (LocalDate.ofEpochDay(initialStartDate).dayOfWeek.value - 1))
    }
    var endType by remember { mutableStateOf(initialEndType) }
    var untilEpoch by remember { mutableStateOf(initialUntil ?: initialStartDate) }
    var countStr by remember { mutableStateOf((initialCount ?: 10).toString()) }
    var unitExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }

    val unitLabel = when (unit) {
        "DAILY" -> stringResource(R.string.s2_unit_days)
        "WEEKLY" -> stringResource(R.string.s2_unit_weeks)
        "MONTHLY" -> stringResource(R.string.s2_unit_months)
        else -> stringResource(R.string.s2_unit_years)
    }
    val endLabel = when (endType) {
        "UNTIL" -> stringResource(R.string.s2_rec_end_until)
        "COUNT" -> stringResource(R.string.s2_rec_end_count)
        else -> stringResource(R.string.s2_rec_end_never)
    }

    val intervalValue = intervalStr.toIntOrNull()
    val countValue = countStr.toIntOrNull()
    val valid = intervalValue != null && intervalValue >= 1 &&
        !(unit == "WEEKLY" && mask == 0) &&
        !(endType == "COUNT" && (countValue == null || countValue < 1)) &&
        !(endType == "UNTIL" && untilEpoch < initialStartDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s2_rec_dialog_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intervalStr,
                        onValueChange = { intervalStr = it },
                        label = { Text(stringResource(R.string.s2_rec_every)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }, modifier = Modifier.weight(1.5f)) {
                        OutlinedTextField(
                            value = unitLabel, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_unit_days)) }, onClick = { unit = "DAILY"; unitExpanded = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_unit_weeks)) }, onClick = { unit = "WEEKLY"; unitExpanded = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_unit_months)) }, onClick = { unit = "MONTHLY"; unitExpanded = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.s2_unit_years)) }, onClick = { unit = "YEARLY"; unitExpanded = false })
                        }
                    }
                }

                if (unit == "WEEKLY") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s2_rec_on), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    WeekdayToggleRow(mask = mask, onToggle = { bit -> mask = mask xor (1 shl bit) })
                }

                if (unit == "MONTHLY") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = monthlyMode == "MONTHLY_DAY", onClick = { monthlyMode = "MONTHLY_DAY" }, label = { Text(stringResource(R.string.s2_rec_monthly_day)) })
                        FilterChip(selected = monthlyMode == "MONTHLY_NTH_WEEKDAY", onClick = { monthlyMode = "MONTHLY_NTH_WEEKDAY" }, label = { Text(stringResource(R.string.s2_rec_monthly_nth)) })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.s2_rec_ends), style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(expanded = endExpanded, onExpandedChange = { endExpanded = it }) {
                    OutlinedTextField(
                        value = endLabel, onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = endExpanded, onDismissRequest = { endExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_end_never)) }, onClick = { endType = "NEVER"; endExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_end_until)) }, onClick = { endType = "UNTIL"; endExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.s2_rec_end_count)) }, onClick = { endType = "COUNT"; endExpanded = false })
                    }
                }

                if (endType == "UNTIL") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(LocalDate.ofEpochDay(untilEpoch).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        OutlinedButton(onClick = { showUntilPicker = true }) { Text(stringResource(R.string.change)) }
                    }
                }
                if (endType == "COUNT") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countStr,
                        onValueChange = { countStr = it },
                        label = { Text(stringResource(R.string.s2_rec_occurrences)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val type = if (unit == "MONTHLY") monthlyMode else unit
                    var shifted = LocalDate.ofEpochDay(initialStartDate)
                    if (unit == "WEEKLY") {
                        while ((mask and (1 shl (shifted.dayOfWeek.value - 1))) == 0) {
                            shifted = shifted.plusDays(1)
                        }
                    }
                    onSave(intervalValue ?: 1, type, if (unit == "WEEKLY") mask else 0, endType, untilEpoch, countValue, shifted.toEpochDay())
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (showUntilPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(untilEpoch).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        CustomDatePickerDialog(
            state = state,
            onDismiss = { showUntilPicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    untilEpoch = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
                }
                showUntilPicker = false
            }
        )
    }
}
