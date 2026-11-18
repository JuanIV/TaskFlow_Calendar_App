package com.example.taskflow.ui.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.EventOccurrence
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Composable
fun DayPage(
    date: LocalDate,
    todayEpoch: Long,
    hourHeight: Dp,
    scrollState: ScrollState,
    tasks: List<TaskEntity>,
    occurrencesFor: (Long, Long) -> Flow<List<EventOccurrence>>,
    onEventClick: (Long, Long) -> Unit,
    onSlotClick: (LocalDate, Int) -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
    onTaskClick: (TaskEntity) -> Unit
) {
    val epoch = date.toEpochDay()
    val flow = remember(epoch) { occurrencesFor(epoch, epoch) }
    val occurrences by flow.collectAsState(initial = emptyList())
    val byDay = remember(occurrences) { groupByDay(occurrences) }
    val strip = (byDay[epoch] ?: emptyList()).filter { it.isStripEvent() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (strip.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                strip.forEach { event ->
                    EventChip(event = event, fontSize = 12.sp, onClick = { onEventClick(event.eventId, event.originalDate) })
                }
            }
        }
        if (tasks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                tasks.forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onTaskClick(task) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleTask(task) })
                        Text(
                            task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        TimeGrid(
            days = listOf(date),
            eventsByDay = byDay,
            todayEpoch = todayEpoch,
            hourHeight = hourHeight,
            scrollState = scrollState,
            onEventClick = onEventClick,
            onSlotClick = onSlotClick,
            modifier = Modifier.weight(1f)
        )
    }
}
