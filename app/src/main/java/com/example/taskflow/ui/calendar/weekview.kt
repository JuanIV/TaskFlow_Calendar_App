package com.example.taskflow.ui.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskflow.R
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.isVisibleIn
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Composable
fun WeekPage(
    weekStart: LocalDate,
    todayEpoch: Long,
    hourHeight: Dp,
    scrollState: ScrollState,
    occurrencesFor: (Long, Long) -> Flow<List<EventOccurrence>>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (Long, Long) -> Unit,
    onSlotClick: (LocalDate, Int) -> Unit
) {
    val firstDay = LocalFirstDay.current
    val days = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val from = weekStart.toEpochDay()
    val flow = remember(from) { occurrencesFor(from, from + 6) }
    val occurrences by flow.collectAsState(initial = emptyList())
    val byDay = remember(occurrences) { groupByDay(occurrences.filter { it.isVisibleIn("WEEK") }) }
    val headers = weekdayHeaderRes(firstDay).map { stringResource(it) }
    val stripVisible = days.any { d -> (byDay[d.toEpochDay()] ?: emptyList()).any { it.isStripEvent() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Spacer(modifier = Modifier.width(44.dp))
            days.forEachIndexed { index, date ->
                val isToday = date.toEpochDay() == todayEpoch
                Column(
                    modifier = Modifier.weight(1f).clickable { onDayClick(date) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(headers[index], fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            fontSize = 13.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        if (stripVisible) {
            Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Spacer(modifier = Modifier.width(44.dp))
                days.forEach { date ->
                    val strip = (byDay[date.toEpochDay()] ?: emptyList()).filter { it.isStripEvent() }
                    Column(modifier = Modifier.weight(1f)) {
                        strip.take(2).forEach { event ->
                            EventChip(event = event, fontSize = 9.sp, onClick = { onEventClick(event.eventId, event.originalDate) })
                        }
                        if (strip.size > 2) {
                            Text(
                                stringResource(R.string.s2_more_count, strip.size - 2),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        TimeGrid(
            days = days,
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
