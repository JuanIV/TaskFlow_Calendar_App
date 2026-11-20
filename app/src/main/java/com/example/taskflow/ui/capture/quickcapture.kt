package com.example.taskflow.ui.capture

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    // Primera linea = titulo. El resto, si lo hay, va a las notas. La tarea queda sin fecha.
    fun add(raw: String, date: Long?) {
        val lines = raw.trim().lines()
        val title = lines.first().trim().take(200)
        if (title.isEmpty()) return
        val notes = lines.drop(1).joinToString("\n").trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            repository.saveTask(TaskEntity(title = title, date = date, createdAt = System.currentTimeMillis(), notes = notes))
        }
    }
}

@Composable
fun QuickCaptureDialog(
    initialText: String,
    date: Long? = null,
    onDismiss: () -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel()
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    val context = LocalContext.current
    val savedMsg = stringResource(R.string.s3_capture_saved)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s3_capture_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.s3_capture_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    viewModel.add(text, date)
                    Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
