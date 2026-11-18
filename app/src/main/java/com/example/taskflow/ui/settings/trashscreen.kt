package com.example.taskflow.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashHistoryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val tasks by viewModel.deletedTasks.collectAsState()
    val events by viewModel.deletedEvents.collectAsState()
    var showConfirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    if (tasks.isNotEmpty() || events.isNotEmpty()) {
                        IconButton(onClick = { showConfirmEmpty = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.empty_trash))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty() && events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                items(tasks) { task ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(task.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { viewModel.restoreTask(task.id) }) {
                                Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore))
                            }
                            IconButton(onClick = { viewModel.deletePermanentlyTask(task.id) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_permanently), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                items(events) { event ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(event.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { viewModel.restoreEvent(event.id) }) {
                                Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore))
                            }
                            IconButton(onClick = { viewModel.deletePermanentlyEvent(event.id) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_permanently), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmEmpty) {
        AlertDialog(
            onDismissRequest = { showConfirmEmpty = false },
            title = { Text(stringResource(R.string.empty_trash)) },
            text = { Text(stringResource(R.string.confirm_empty_trash)) },
            confirmButton = {
                TextButton(onClick = { viewModel.emptyTrash(); showConfirmEmpty = false }) {
                    Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmEmpty = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
