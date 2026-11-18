package com.example.taskflow.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.domain.normalizeText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: TrashHistoryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val tasks by viewModel.completedHistory.collectAsState()
    var query by remember { mutableStateOf("") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale("es", "ES")) }

    val filtered = remember(tasks, query) {
        val q = normalizeText(query)
        if (q.isBlank()) tasks else tasks.filter { normalizeText(it.title).contains(q) }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { task ->
            Instant.ofEpochMilli(task.completedAt ?: 0L).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.s2_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.s2_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.s2_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    grouped.forEach { (day: LocalDate, list) ->
                        item(key = "hdr_${day.toEpochDay()}") {
                            Text(
                                day.format(dateFormat).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { it.id }) { task ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(task.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    TextButton(onClick = { viewModel.markPending(task.id) }) {
                                        Text(stringResource(R.string.s2_mark_pending))
                                    }
                                    IconButton(onClick = { viewModel.deleteTaskToTrash(task.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
