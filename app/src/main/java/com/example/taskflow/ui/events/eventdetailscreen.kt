package com.example.taskflow.ui.events

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.domain.buildReadableRecurrence
import com.example.taskflow.domain.formatMinute
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long = 0L,
    originalDate: Long = 0L,
    viewModel: EventDetailViewModel = hiltViewModel(),
    actionViewModel: EventViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isLoading by viewModel.isLoading.collectAsState()
    val occurrenceState by viewModel.occurrenceState.collectAsState()
    val baseState by viewModel.baseEvent.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteChoice by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    val deletedMsg = stringResource(R.string.s2_event_deleted)
    val undoLabel = stringResource(R.string.s2_undo)
    val mapsMissing = stringResource(R.string.s2_maps_missing)

    LaunchedEffect(eventId, originalDate) { viewModel.loadOccurrence(eventId, originalDate) }

    val occ = occurrenceState
    val base = baseState
    val showContent = !deleted && !isLoading && occ != null && base != null

    fun runDelete(doDelete: () -> Unit, undo: () -> Unit) {
        deleted = true
        doDelete()
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMsg,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                undo()
                deleted = false
            } else {
                onBack()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                },
                actions = {
                    if (showContent) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.s2_edit))
                        }
                        IconButton(onClick = {
                            if (base != null && base.recurrenceType != "NONE") showDeleteChoice = true else showDeleteConfirm = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!showContent) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        deleted -> deletedMsg
                        isLoading -> stringResource(R.string.s2_event_loading)
                        else -> stringResource(R.string.s2_event_not_found)
                    }
                )
            }
        } else if (occ != null && base != null) {
            val dateText = LocalDate.ofEpochDay(occ.startDate)
                .format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale("es", "ES")))
            val startMin = occ.startMinute
            val endMin = occ.endMinute
            val timeText = if (occ.allDay || startMin == null) {
                stringResource(R.string.s2_all_day)
            } else {
                formatMinute(startMin) +
                    (if (endMin != null) " - " + formatMinute(endMin) else "")
            }
            val recurrenceText = buildReadableRecurrence(context, base)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(16.dp).background(Color(occ.colorArgb), CircleShape))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(occ.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            stringResource(
                                if (occ.priority == "SECONDARY") R.string.s2_importance_secondary else R.string.s2_importance_important
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(dateText, style = MaterialTheme.typography.bodyLarge)
                        Text(timeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (recurrenceText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.s2_recurrence_label) + ": " + recurrenceText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (reminders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s2_reminders), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    reminders.forEach { r ->
                        Text(reminderLabel(r.offsetMinutes, occ.allDay), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                val loc = occ.location
                if (!loc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(loc, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(loc))))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, mapsMissing, Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(stringResource(R.string.s2_open_maps)) }
                    }
                }

                val notes = occ.notes
                if (!notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s2_event_notes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(notes, style = MaterialTheme.typography.bodyLarge)
                    Regex("https?://\\S+").findAll(notes).map { it.value }.toList().forEach { url ->
                        TextButton(onClick = {
                            try {
                                uriHandler.openUri(url)
                            } catch (e: Exception) {
                                Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(url) }
                    }
                }
            }
        }
    }

    if (showEdit && occ != null && base != null) {
        EventDialog(
            initialDate = LocalDate.ofEpochDay(occ.startDate),
            baseEvent = base,
            occurrenceToEdit = occ,
            onDismiss = { showEdit = false }
        )
    }

    if (showDeleteConfirm && base != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.s2_delete_event_title)) },
            text = { Text(stringResource(R.string.s2_delete_event_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    runDelete({ actionViewModel.softDelete(eventId) }, { actionViewModel.restore(eventId) })
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showDeleteChoice && base != null && occ != null) {
        RecurringActionDialog(
            isDelete = true,
            onDismiss = { showDeleteChoice = false },
            onActionSelected = { choice ->
                showDeleteChoice = false
                when (choice) {
                    "THIS_EVENT" -> runDelete(
                        { actionViewModel.deleteOccurrence(eventId, occ.originalDate) },
                        { actionViewModel.undeleteOccurrence(eventId, occ.originalDate) }
                    )
                    "THIS_AND_FOLLOWING" -> {
                        if (occ.originalDate == base.date) {
                            runDelete({ actionViewModel.softDelete(eventId) }, { actionViewModel.restore(eventId) })
                        } else {
                            runDelete(
                                { actionViewModel.truncate(eventId, occ.originalDate - 1) },
                                { actionViewModel.restoreRow(base) }
                            )
                        }
                    }
                    else -> runDelete({ actionViewModel.softDelete(eventId) }, { actionViewModel.restore(eventId) })
                }
            }
        )
    }
}
