package com.example.taskflow.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskflow.R
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.isVisibleIn
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthPage(
    month: YearMonth,
    todayEpoch: Long,
    pendingTasksByDate: Map<Long, Int>,
    occurrencesFor: (Long, Long) -> Flow<List<EventOccurrence>>,
    onDayClick: (LocalDate) -> Unit
) {
    val firstDay = LocalFirstDay.current
    val days = remember(month, firstDay) { getDaysInMonthGrid(month, firstDay) }
    val from = days.first().toEpochDay()
    val to = days.last().toEpochDay()
    val flow = remember(from, to) { occurrencesFor(from, to) }
    val occurrences by flow.collectAsState(initial = emptyList())
    val byDay = remember(occurrences) { groupByDay(occurrences.filter { it.isVisibleIn("MONTH") }) }
    val headers = weekdayHeaderRes(firstDay).map { stringResource(it) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            headers.forEach { header ->
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0..6) {
                    val date = days[week * 7 + col]
                    val epoch = date.toEpochDay()
                    DayCell(
                        date = date,
                        isCurrentMonth = date.month == month.month,
                        isToday = epoch == todayEpoch,
                        events = byDay[epoch] ?: emptyList(),
                        pendingTasks = pendingTasksByDate[epoch] ?: 0,
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    events: List<EventOccurrence>,
    pendingTasks: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberColor = when {
        isToday -> MaterialTheme.colorScheme.onPrimary
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = numberColor,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (pendingTasks > 0) {
                Box(
                    modifier = Modifier
                        .padding(end = 3.dp)
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                )
            }
        }
        events.take(3).forEach { event ->
            EventChip(event = event, fontSize = 9.sp, onClick = onClick)
        }
        if (events.size > 3) {
            Text(
                text = stringResource(R.string.s2_more_count, events.size - 3),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
