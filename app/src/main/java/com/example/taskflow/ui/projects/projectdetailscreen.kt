package com.example.taskflow.ui.projects

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.DayOfWeek
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.ProjectNodeEntity
import com.example.taskflow.domain.ListFilter
import com.example.taskflow.domain.ListRow
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.PlanStatus
import com.example.taskflow.domain.ProjectStatus
import com.example.taskflow.domain.buildCsv
import com.example.taskflow.domain.buildListRows
import com.example.taskflow.domain.formatHours
import com.example.taskflow.domain.hasChildren
import com.example.taskflow.domain.partLabel
import com.example.taskflow.domain.subtreeHasProgress
import com.example.taskflow.domain.todayParts

// Acciones de estado disponibles desde el estado actual: pares (estado destino, texto)
private fun statusOptions(status: String): List<Pair<String, Int>> = when (status) {
    ProjectStatus.ACTIVE -> listOf(
        ProjectStatus.PAUSED to R.string.p1c_action_pause,
        ProjectStatus.COMPLETED to R.string.p1c_action_complete,
        ProjectStatus.ARCHIVED to R.string.p1c_action_archive
    )
    ProjectStatus.PAUSED -> listOf(
        ProjectStatus.ACTIVE to R.string.p1c_action_resume,
        ProjectStatus.COMPLETED to R.string.p1c_action_complete,
        ProjectStatus.ARCHIVED to R.string.p1c_action_archive
    )
    ProjectStatus.COMPLETED -> listOf(
        ProjectStatus.ACTIVE to R.string.p1c_action_reopen,
        ProjectStatus.ARCHIVED to R.string.p1c_action_archive
    )
    else -> listOf(ProjectStatus.ACTIVE to R.string.p1c_action_reactivate)
}

private fun filterLabelRes(filter: ListFilter): Int = when (filter) {
    ListFilter.ALL -> R.string.p1c_filter_all
    ListFilter.PENDING -> R.string.p1c_filter_pending
    ListFilter.TODAY -> R.string.p1c_filter_today
    ListFilter.LATE -> R.string.p1c_filter_late
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val plan by viewModel.plan.collectAsState()
    val excluded by viewModel.excludedDays.collectAsState()
    val today by viewModel.today.collectAsState()
    val pullCandidate by viewModel.pullCandidate.collectAsState()
    val dependencies by viewModel.dependencies.collectAsState()
    val deviation by viewModel.deviation.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val gridDays by viewModel.gridDays.collectAsState()
    val weekView by viewModel.weekView.collectAsState()
    val editMode by viewModel.editMode.collectAsState()
    val advanced = viewModel.isAdvanced()
    val firstDayOfWeek = DayOfWeek.of(
        viewModel.firstDayOfWeekValue.collectAsState().value.coerceIn(1, 7)
    )

    var filterName by rememberSaveable { mutableStateOf(ListFilter.ALL.name) }
    var collapsed by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditProject by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var duplicateAsTemplate by remember { mutableStateOf(false) }
    var showDeleteProject by remember { mutableStateOf(false) }
    var showNewNode by remember { mutableStateOf(false) }
    var newNodeParent by remember { mutableStateOf<Long?>(null) }
    var editingNode by remember { mutableStateOf<ProjectNodeEntity?>(null) }
    var deletingNodeId by remember { mutableStateOf<Long?>(null) }
    var movingNodeId by remember { mutableStateOf<Long?>(null) }
    var depNodeId by remember { mutableStateOf<Long?>(null) }
    var exportRequested by remember { mutableStateOf(false) }
    var shareGridRequested by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val rows = viewModel.exportRows()
                val header = listOf(
                    "proyecto", "nivel", "titulo", "tipo", "duracion_h", "progreso_pct",
                    "inicio", "fin", "completado", "predecesores", "critico"
                )
                val name = viewModel.project.value?.name ?: ""
                val body = buildCsv(header, rows.map { listOf(name) + it.drop(1) })
                context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, context.getString(R.string.p2d_export_ok), Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (exportRequested) {
        exportRequested = false
        csvLauncher.launch((project?.name ?: "proyecto") + ".csv")
    }

    val currentProject = project
    val currentPlan = plan
    if (currentProject == null || currentPlan == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.p1c_loading))
        }
        return
    }

    val activeFilter = ListFilter.valueOf(filterName)
    val titles = nodes.associate { it.id to it.title }
    val todayFirst = todayParts(currentPlan, today).firstOrNull()
    val todayText = if (todayFirst != null) partLabel(titles[todayFirst.nodeId].orEmpty(), todayFirst) else null
    val rows = buildListRows(nodes, currentPlan, currentProject.deadlineDate, today, activeFilter, collapsed)
    val statusText = projectStatusMessage(
        context,
        currentProject.status,
        currentPlan.status,
        currentPlan.marginDays,
        currentPlan.overdueDays
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentProject.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showEditProject = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.s2_edit))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            statusOptions(currentProject.status).forEach { (target, labelRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.setStatus(target) { result -> showOpResult(context, result) }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.p1c_action_duplicate)) },
                                onClick = { menuExpanded = false; duplicateAsTemplate = false; showDuplicate = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.p1c_action_duplicate_template)) },
                                onClick = { menuExpanded = false; duplicateAsTemplate = true; showDuplicate = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.p2d_export_csv)) },
                                onClick = { menuExpanded = false; exportRequested = true }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.p1c_action_delete_project),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { menuExpanded = false; showDeleteProject = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newNodeParent = null; showNewNode = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.p1c_add_root))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    LinearProgressIndicator(
                        progress = { currentPlan.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.p1c_progress, currentPlan.progressPercent))
                    val expectedEnd = currentPlan.expectedEndDay
                    if (expectedEnd != null) {
                        Text(stringResource(R.string.p1c_expected_end, formatDay(expectedEnd)))
                    }
                    Text(
                        statusText,
                        color = if (currentPlan.status == PlanStatus.ATRASADO) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(stringResource(R.string.p1c_remaining, formatHours(currentPlan.remainingMinutes)))
                    val dev = deviation
                    if (dev != null && dev.sampleCount > 0) {
                        Text(
                            stringResource(
                                R.string.p2e_deviation,
                                com.example.taskflow.domain.formatDeviation(dev.deviationPercent),
                                dev.sampleCount
                            )
                        )
                    }
                    if (todayText != null) {
                        Text(
                            stringResource(R.string.p1c_today_prefix, todayText),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val candidate = pullCandidate
                    if (candidate != null) {
                        OutlinedButton(onClick = { viewModel.pullForward(candidate) { } }) {
                            Text(stringResource(R.string.p1c_pull_forward))
                        }
                    }
                }
            }

            if (advanced) {
                val days = gridDays
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = weekView,
                        onClick = { viewModel.toggleWeekView() },
                        enabled = !editMode,
                        label = {
                            Text(stringResource(if (weekView) R.string.p4_grid_switch_day else R.string.p4_grid_switch_week))
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!editMode) {
                        IconButton(onClick = { viewModel.enterGridEditMode() }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.p6_edit))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { shareGridRequested = true }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.p7_share_grid))
                        }
                    } else {
                        TextButton(onClick = { viewModel.cancelGridEdit() }) {
                            Text(stringResource(R.string.p6_cancel))
                        }
                        TextButton(onClick = {
                            viewModel.saveGridEdit { result -> showOpResult(context, result) }
                        }) {
                            Text(stringResource(R.string.p6_save))
                        }
                    }
                }
                if (editMode) {
                    Text(
                        stringResource(R.string.p6_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                if (days != null) {
                    ProjectGridView(
                        rows = gridRows,
                        days = days,
                        weekView = weekView,
                        today = today,
                        firstDay = firstDayOfWeek,
                        onToggleCollapse = { viewModel.toggleCollapsedGrid(it) },
                        editMode = editMode,
                        onCellTap = { nodeId, day -> viewModel.toggleGridCell(nodeId, day) }
                    )
                }
                if (shareGridRequested) {
                    shareGridRequested = false
                    val d = gridDays
                    if (d != null) {
                        val file = renderGridPng(
                            context,
                            currentProject.name.replace(' ', '_') + "_grid.png",
                            gridRows,
                            d,
                            weekView,
                            firstDayOfWeek
                        )
                        shareGridPng(context, file)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ListFilter.values().forEach { f ->
                        FilterChip(
                            selected = activeFilter == f,
                            onClick = { filterName = f.name },
                            label = { Text(stringResource(filterLabelRes(f))) }
                        )
                    }
                }

                if (rows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.p1c_empty_tree), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(rows, key = { it.nodeId }) { row ->
                            ListRowView(
                                row = row,
                                onToggleExpand = {
                                    collapsed = if (collapsed.contains(row.nodeId)) collapsed - row.nodeId else collapsed + row.nodeId
                                },
                                onToggleCompleted = {
                                    viewModel.setNodeCompleted(row.nodeId, !row.completed, null) { result ->
                                        showOpResult(context, result)
                                    }
                                },
                                onAddChild = { newNodeParent = row.nodeId; showNewNode = true },
                                onEdit = { editingNode = nodes.firstOrNull { it.id == row.nodeId } },
                                onMove = { movingNodeId = row.nodeId },
                                onDependencies = { depNodeId = row.nodeId },
                                onDelete = { deletingNodeId = row.nodeId },
                                onUp = { viewModel.moveSibling(row.nodeId, -1) { result -> showOpResult(context, result) } },
                                onDown = { viewModel.moveSibling(row.nodeId, 1) { result -> showOpResult(context, result) } }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    if (showEditProject) {
        ProjectFormDialog(
            existing = currentProject,
            existingExcludedDays = excluded,
            todayEpoch = today,
            onDismiss = { showEditProject = false },
            onSave = { form ->
                viewModel.saveProject(form) { result ->
                    if (result == OpResult.OK) showEditProject = false else showOpResult(context, result)
                }
            }
        )
    }

    if (showDuplicate) {
        NameDialog(
            title = stringResource(
                if (duplicateAsTemplate) R.string.p1c_action_duplicate_template else R.string.p1c_action_duplicate
            ),
            initial = stringResource(R.string.p1c_copy_suffix, currentProject.name),
            onDismiss = { showDuplicate = false },
            onConfirm = { name ->
                viewModel.duplicate(name, duplicateAsTemplate) { id ->
                    showDuplicate = false
                    val message = if (id != null) R.string.p1c_dup_done else R.string.p1c_dup_fail
                    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showDeleteProject) {
        AlertDialog(
            onDismissRequest = { showDeleteProject = false },
            title = { Text(stringResource(R.string.p1c_delete_project_title)) },
            text = { Text(stringResource(R.string.p1c_delete_project_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteProject = false
                    viewModel.softDelete { onBack() }
                }) {
                    Text(stringResource(R.string.p1c_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteProject = false }) { Text(stringResource(R.string.p1c_cancel)) } }
        )
    }

    if (showNewNode) {
        NodeFormDialog(
            existing = null,
            isGroup = false,
            onDismiss = { showNewNode = false },
            onSave = { draft ->
                viewModel.addNode(newNodeParent, draft) { result ->
                    if (result == OpResult.OK) showNewNode = false else showOpResult(context, result)
                }
            }
        )
    }

    val editing = editingNode
    if (editing != null) {
        NodeFormDialog(
            existing = editing,
            isGroup = hasChildren(nodes, editing.id),
            onDismiss = { editingNode = null },
            onSave = { draft ->
                viewModel.updateNode(editing.id, draft) { result ->
                    if (result == OpResult.OK) editingNode = null else showOpResult(context, result)
                }
            }
        )
    }

    val deleting = deletingNodeId
    if (deleting != null) {
        ConfirmDeleteDialog(
            withProgress = subtreeHasProgress(nodes, deleting),
            onDismiss = { deletingNodeId = null },
            onConfirm = {
                viewModel.deleteNode(deleting, true) { result ->
                    deletingNodeId = null
                    showOpResult(context, result)
                }
            }
        )
    }

    val moving = movingNodeId
    if (moving != null) {
        MoveNodeDialog(
            targets = viewModel.moveTargets(moving),
            onDismiss = { movingNodeId = null },
            onSelect = { parentId ->
                viewModel.moveNode(moving, parentId, Int.MAX_VALUE) { result ->
                    movingNodeId = null
                    showOpResult(context, result)
                }
            }
        )
    }

    val depNode = depNodeId
    val depSuccessor = if (depNode == null) null else nodes.firstOrNull { it.id == depNode }
    if (depSuccessor != null) {
        DependencyDialog(
            successor = depSuccessor,
            nodes = nodes,
            dependencies = dependencies,
            onToggle = { predecessorId, enabled ->
                viewModel.setDependency(predecessorId, depSuccessor.id, enabled) { result ->
                    if (result == OpResult.CYCLE) {
                        Toast.makeText(context, context.getString(R.string.p2a_err_dep_cycle), Toast.LENGTH_SHORT).show()
                    } else {
                        showOpResult(context, result)
                    }
                }
            },
            onDismiss = { depNodeId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListRowView(
    row: ListRow,
    onToggleExpand: () -> Unit,
    onToggleCompleted: () -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDependencies: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val durationText = if (!row.isGroup && !row.isMilestone) {
        stringResource(R.string.p1c_duration, formatHours(row.estimatedMinutes))
    } else {
        null
    }
    val startDay = row.startDay
    val endDay = row.endDay
    val datesText = if (startDay != null && endDay != null) {
        stringResource(R.string.p1c_dates, formatDay(startDay), formatDay(endDay))
    } else {
        null
    }
    val milestoneText = if (row.isMilestone) stringResource(R.string.p1c_milestone_label) else null
    val criticalText = if (row.critical) stringResource(R.string.p2a_critical) else null
    val subtitleParts = listOfNotNull(milestoneText, criticalText, durationText, datesText)
    val todayLabel = row.todayLabel

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = (row.depth * 16).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            if (row.isGroup) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (row.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            } else {
                Checkbox(checked = row.completed, onCheckedChange = { onToggleCompleted() })
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (row.completed) TextDecoration.LineThrough else TextDecoration.None
            )
            if (row.isGroup) {
                LinearProgressIndicator(
                    progress = { row.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (subtitleParts.isNotEmpty()) {
                Text(
                    subtitleParts.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (row.isGroup) {
                Text(
                    stringResource(R.string.p1c_progress, row.progressPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (todayLabel != null) {
                Text(
                    stringResource(R.string.p1c_today_prefix, todayLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_more))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.p1c_action_edit)) },
                    onClick = { menuExpanded = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.p1c_action_add_child)) },
                    onClick = { menuExpanded = false; onAddChild() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.p1c_action_move)) },
                    onClick = { menuExpanded = false; onMove() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.p2a_action_deps)) },
                    onClick = { menuExpanded = false; onDependencies() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.p1c_action_up)) },
                    onClick = { menuExpanded = false; onUp() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.p1c_action_down)) },
                    onClick = { menuExpanded = false; onDown() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.p1c_action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}
