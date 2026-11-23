package com.example.taskflow.ui.projects

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.PlanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onOpenProject: (Long) -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val cards by viewModel.cards.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val today by viewModel.today.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var templateChosen by remember { mutableStateOf<ProjectEntity?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.p1c_tab_title)) },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.p1c_templates)) },
                                onClick = { menuExpanded = false; showTemplates = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.p1c_new_project))
            }
        }
    ) { innerPadding ->
        if (cards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.p1c_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
                items(cards, key = { it.project.id }) { card ->
                    ProjectCardView(card = card, onClick = { onOpenProject(card.project.id) })
                }
                item(key = "spacer_end") { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }

    if (showNew) {
        ProjectFormDialog(
            existing = null,
            existingExcludedDays = emptyList(),
            todayEpoch = today,
            onDismiss = { showNew = false },
            onSave = { form ->
                viewModel.saveProject(null, form) { result ->
                    if (result == OpResult.OK) showNew = false else showOpResult(context, result)
                }
            }
        )
    }

    if (showTemplates) {
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            title = { Text(stringResource(R.string.p1c_templates)) },
            text = {
                if (templates.isEmpty()) {
                    Text(stringResource(R.string.p1c_no_templates))
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        templates.forEach { template ->
                            TextButton(
                                onClick = { showTemplates = false; templateChosen = template },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(template.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTemplates = false }) { Text(stringResource(R.string.p1c_cancel)) } }
        )
    }

    val chosen = templateChosen
    if (chosen != null) {
        NameDialog(
            title = stringResource(R.string.p1c_create_from_template),
            initial = chosen.name,
            onDismiss = { templateChosen = null },
            onConfirm = { name ->
                viewModel.duplicate(chosen.id, name, false) { id ->
                    templateChosen = null
                    val message = if (id != null) R.string.p1c_dup_done else R.string.p1c_dup_fail
                    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCardView(card: ProjectCard, onClick: () -> Unit) {
    val context = LocalContext.current
    val project = card.project
    val summary = card.summary
    val statusText = projectStatusMessage(
        context,
        project.status,
        summary.status,
        summary.marginDays,
        summary.overdueDays
    )
    val todayLabel = summary.todayLabel
    val expectedEnd = summary.expectedEndDay

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(8.dp).fillMaxHeight().background(Color(project.colorArgb)))
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { summary.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.p1c_progress, summary.progressPercent),
                    style = MaterialTheme.typography.bodySmall
                )
                if (expectedEnd != null) {
                    Text(
                        stringResource(R.string.p1c_expected_end, formatDay(expectedEnd)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary.status == PlanStatus.ATRASADO) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (todayLabel != null) {
                    Text(
                        stringResource(R.string.p1c_today_prefix, todayLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (summary.todayExtraCount > 0) {
                    Text(
                        stringResource(R.string.p1c_more_count, summary.todayExtraCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Dialogo con un solo campo de texto (nombre). Se usa para duplicar y crear desde plantilla.
@Composable
fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.p1c_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(stringResource(R.string.p1c_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p1c_cancel)) } }
    )
}
