package com.example.taskflow.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.ui.tasks.TaskDialog
import com.example.taskflow.ui.tasks.TasksViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToEvent: (Long, Long) -> Unit,
    onNavigateToRoutine: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    tasksViewModel: TasksViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val allTasks by tasksViewModel.allActiveTasks.collectAsState()
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    val groups = listOf(
        Pair("EVENT", R.string.s2_events),
        Pair("TASK", R.string.s2_tasks),
        Pair("ROUTINE", R.string.s2_routines_title)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.updateQuery(it) },
                        placeholder = { Text(stringResource(R.string.s2_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.s2_remove))
                                }
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        if (query.isNotBlank() && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.s2_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                groups.forEach { (type, headerRes) ->
                    val items = results.filter { it.type == type }
                    if (items.isNotEmpty()) {
                        item(key = "hdr_$type") {
                            Text(
                                stringResource(headerRes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(items, key = { it.type + "_" + it.id }) { item ->
                            val date = item.displayDate
                            ListItem(
                                headlineContent = { Text(item.title) },
                                supportingContent = if (date != null) ({ Text(LocalDate.ofEpochDay(date).format(dateFormat)) }) else null,
                                modifier = Modifier.clickable {
                                    when (item.type) {
                                        "EVENT" -> onNavigateToEvent(item.id, item.extra)
                                        "ROUTINE" -> onNavigateToRoutine(item.id)
                                        else -> editingTask = allTasks.firstOrNull { it.id == item.id }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    val editing = editingTask
    if (editing != null) {
        TaskDialog(
            taskToEdit = editing,
            existingSubtasks = allTasks.filter { it.parentTaskId == editing.id }.sortedBy { it.sortOrder },
            onDismiss = { editingTask = null },
            onSave = { result -> tasksViewModel.saveTaskFromResult(result) },
            onDelete = { id -> tasksViewModel.deleteTask(id); editingTask = null },
            onToggleSubtask = { tasksViewModel.toggleTaskCompletion(it) },
            onDeleteSubtask = { tasksViewModel.deleteTask(it) }
        )
    }
}
