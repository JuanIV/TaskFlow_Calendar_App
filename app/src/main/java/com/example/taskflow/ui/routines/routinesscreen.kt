package com.example.taskflow.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.ui.components.IconKeys
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel = hiltViewModel(),
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToTemplates: () -> Unit,
    onOpenSearch: () -> Unit = {}
) {
    val cards by viewModel.cards.collectAsState()
    val todayEpoch by viewModel.todayEpoch.collectAsState()
    val dayStartHour by viewModel.dayStartHour.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var finishedExpanded by remember { mutableStateOf(false) }

    val active = cards.filter { val e = it.routine.endDate; e == null || e >= todayEpoch }
    val finished = cards.filter { val e = it.routine.endDate; e != null && e < todayEpoch }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_routines)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.s2_search))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.s2_templates)) },
                                onClick = { menuExpanded = false; onNavigateToTemplates() }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.s2_routine_new))
            }
        }
    ) { innerPadding ->
        if (cards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.s2_routines_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
                items(active, key = { it.routine.id }) { card ->
                    RoutineCardView(card, todayEpoch) { onNavigateToDetail(card.routine.id) }
                }
                if (finished.isNotEmpty()) {
                    item(key = "hdr_finished") {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { finishedExpanded = !finishedExpanded }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.s2_routines_finished, finished.size),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(if (finishedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                    }
                    if (finishedExpanded) {
                        items(finished, key = { "f${it.routine.id}" }) { card ->
                            RoutineCardView(card, todayEpoch) { onNavigateToDetail(card.routine.id) }
                        }
                    }
                }
                item(key = "spacer_end") { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }

    if (showDialog) {
        RoutineDialog(
            initialAnchorDate = todayEpoch,
            dayStartHour = dayStartHour,
            onDismiss = { showDialog = false },
            onSave = { routine -> viewModel.saveRoutine(routine) { showDialog = false } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineCardView(card: RoutineCardInfo, todayEpoch: Long, onClick: () -> Unit) {
    val r = card.routine
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val pausedFrom = r.pausedFromEpochDay
    val pausedUntil = r.pausedUntilEpochDay
    val isPaused = pausedFrom != null && todayEpoch >= pausedFrom && (pausedUntil == null || todayEpoch <= pausedUntil)
    val end = r.endMinuteOfDay
    val timeText = stringResource(R.string.s2_routine_from_time, formatMinute(r.startMinuteOfDay)) +
        (if (end != null) stringResource(R.string.s2_routine_until_time, formatMinute(end)) else "")
    val streakText = stringResource(R.string.s2_routine_streak, card.streak)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(8.dp).fillMaxHeight().background(Color(r.colorArgb)))
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconVector = IconKeys.iconFor(r.icon)
                    if (iconVector != null) {
                        Icon(iconVector, contentDescription = null, tint = Color(r.colorArgb), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    recurrenceSummary(r) + "  -  " + timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    isPaused -> Text(
                        if (pausedUntil != null) stringResource(R.string.s2_routine_paused_until, LocalDate.ofEpochDay(pausedUntil).format(dateFormat))
                        else stringResource(R.string.s2_routine_paused),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    !r.isActive -> Text(stringResource(R.string.s2_routine_inactive), style = MaterialTheme.typography.bodyMedium)
                    card.appliesToday -> Text(
                        stringResource(R.string.s2_routine_today_progress, card.done, card.required) + "   " + streakText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> Text(
                        stringResource(R.string.s2_routine_not_today) + "   " + streakText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
