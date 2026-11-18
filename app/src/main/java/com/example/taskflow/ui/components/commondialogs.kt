package com.example.taskflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.taskflow.R

@Composable
fun WeekdayToggleRow(mask: Int, onToggle: (Int) -> Unit) {
    val labels = listOf(R.string.s2_wd_1, R.string.s2_wd_2, R.string.s2_wd_3, R.string.s2_wd_4, R.string.s2_wd_5, R.string.s2_wd_6, R.string.s2_wd_7)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, labelRes ->
            val selected = (mask and (1 shl i)) != 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggle(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(labelRes), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTimePickerDialog(state: TimePickerState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePickerDialog(state: DatePickerState, onDismiss: () -> Unit, onConfirm: () -> Unit, onClear: (() -> Unit)? = null) {
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        Column {
            if (onClear != null) {
                TextButton(onClick = onClear, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) { Text(stringResource(R.string.clear)) }
            }
            DatePicker(state = state)
        }
    }
}
