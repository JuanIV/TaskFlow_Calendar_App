package com.example.taskflow.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.taskflow.ui.components.IconKeys
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.domain.isVisibleIn
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaView(
    todayEpoch: Long,
    tasks: List<TaskEntity>,
    occurrencesFor: (Long, Long) -> Flow<List<EventOccurrence>>,
    onEventClick: (Long, Long) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onToggleTask: (TaskEntity) -> Unit
) {
    var days by remember { mutableStateOf(30) }
    val flow = remember(todayEpoch, days) { occurrencesFor(todayEpoch, todayEpoch + days) }
    val occurrences by flow.collectAsState(initial = emptyList())
    val eventsByDay = remember(occurrences) { groupByDay(occurrences.filter { it.isVisibleIn("AGENDA") }) }
    val topLevel = tasks.filter { it.parentTaskId == null }
    val overdue = topLevel.filter { val d = it.date; d != null && d < todayEpoch && !it.isCompleted }
    val tasksByDay = topLevel.filter { val d = it.date; d != null && d >= todayEpoch && d <= todayEpoch + days }.groupBy { it.date ?: 0L }
    val dayKeys = (eventsByDay.keys + tasksByDay.keys).filter { it >= todayEpoch }.sorted()
    val listState = rememberLazyListState()
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale("es", "ES")) }

    LaunchedEffect(listState) {
        snapshotFlow {
            Pair(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1, listState.layoutInfo.totalItemsCount)
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 3 && days < 365) days += 30
        }
    }

    if (dayKeys.isEmpty() && overdue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.s2_agenda_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (overdue.isNotEmpty()) {
            stickyHeader(key = "hdr_overdue") {
                Text(
                    text = stringResource(R.string.s2_overdue_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(overdue, key = { "o${it.id}" }) { task ->
                AgendaTaskRow(task, onToggleTask)
            }
        }
        dayKeys.forEach { epoch ->
            val date = LocalDate.ofEpochDay(epoch)
            stickyHeader(key = "hdr_$epoch") {
                Text(
                    text = date.format(dateFormat).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onDayClick(date) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(eventsByDay[epoch] ?: emptyList(), key = { "e${epoch}_${it.eventId}_${it.originalDate}" }) { event ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEventClick(event.eventId, event.originalDate) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconVector = IconKeys.iconFor(event.icon)
                    if (iconVector != null) {
                        Icon(iconVector, contentDescription = null, tint = Color(event.colorArgb), modifier = Modifier.size(16.dp))
                    } else {
                        Box(modifier = Modifier.size(12.dp).background(Color(event.colorArgb), CircleShape))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(event.title, style = MaterialTheme.typography.bodyLarge)
                        val start = event.startMinute
                        val end = event.endMinute
                        val timeText = if (event.allDay || start == null) {
                            stringResource(R.string.s2_all_day)
                        } else {
                            formatMinute(start) +
                                (if (end != null) " - " + formatMinute(end) else "")
                        }
                        Text(timeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 44.dp), thickness = 0.5.dp)
            }
            items(tasksByDay[epoch] ?: emptyList(), key = { "t${epoch}_${it.id}" }) { task ->
                AgendaTaskRow(task, onToggleTask)
            }
        }
    }
}

@Composable
private fun AgendaTaskRow(task: TaskEntity, onToggleTask: (TaskEntity) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleTask(task) })
        Text(
            task.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}
