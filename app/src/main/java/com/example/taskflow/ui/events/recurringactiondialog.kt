package com.example.taskflow.ui.events

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.taskflow.R

@Composable
fun RecurringActionDialog(
    isDelete: Boolean,
    onDismiss: () -> Unit,
    onActionSelected: (String) -> Unit // "THIS_EVENT", "THIS_AND_FOLLOWING", "ALL_EVENTS"
) {
    val titleRes = if (isDelete) R.string.delete_recurring_title else R.string.edit_recurring_title

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onActionSelected("THIS_EVENT") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.this_event_only))
                }
                OutlinedButton(onClick = { onActionSelected("THIS_AND_FOLLOWING") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.this_and_following))
                }
                OutlinedButton(onClick = { onActionSelected("ALL_EVENTS") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.all_events))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
