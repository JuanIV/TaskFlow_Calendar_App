package com.example.taskflow.ui.calendar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.formatMinute
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

private fun currentMinuteOfDay(): Int {
    val t = LocalTime.now()
    return t.hour * 60 + t.minute
}

@Composable
fun EventChip(
    event: EventOccurrence,
    fontSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val base = Color(event.colorArgb)
    val secondary = event.priority == "SECONDARY"
    val background = if (secondary) base.copy(alpha = 0.45f) else base
    val foreground = if (secondary) MaterialTheme.colorScheme.onSurface else getTextColorForBackground(base)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Text(event.title, fontSize = fontSize, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EventBlock(
    event: EventOccurrence,
    hourHeight: Dp,
    colWidth: Dp,
    column: Int,
    onClick: () -> Unit
) {
    val start = event.startMinute ?: 0
    val end = event.endMinute
    val duration = if (end != null && end > start) end - start else 30
    val height = maxOf(24.dp, hourHeight * (duration / 60f))
    val base = Color(event.colorArgb)
    val secondary = event.priority == "SECONDARY"
    Box(
        modifier = Modifier
            .offset(x = colWidth * column, y = hourHeight * (start / 60f))
            .width(colWidth)
            .height(height)
            .padding(end = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (secondary) base.copy(alpha = 0.45f) else base.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Text(
            event.title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = if (duration > 30) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            color = if (secondary) MaterialTheme.colorScheme.onSurface else getTextColorForBackground(base)
        )
    }
}

@Composable
fun TimeGrid(
    days: List<LocalDate>,
    eventsByDay: Map<Long, List<EventOccurrence>>,
    todayEpoch: Long,
    hourHeight: Dp,
    scrollState: ScrollState,
    onEventClick: (Long, Long) -> Unit,
    onSlotClick: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gutter = 44.dp
    val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
    val slotClick by rememberUpdatedState(onSlotClick)
    var nowMinute by remember { mutableStateOf(currentMinuteOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMinute = currentMinuteOfDay()
        }
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nowColor = MaterialTheme.colorScheme.error

    Row(modifier = modifier.verticalScroll(scrollState)) {
        Column(modifier = Modifier.width(gutter)) {
            for (hour in 0..23) {
                Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                    if (hour > 0) {
                        Text(
                            formatMinute(hour * 60),
                            fontSize = 10.sp,
                            color = labelColor,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp).offset(y = (-6).dp)
                        )
                    }
                }
            }
        }
        days.forEach { date ->
            val epoch = date.toEpochDay()
            val timed = (eventsByDay[epoch] ?: emptyList()).filter { !it.isStripEvent() }
            val packed = packEvents(timed)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * 24)
                    .drawBehind {
                        for (h in 0..24) {
                            drawLine(lineColor, Offset(0f, h * hourPx), Offset(size.width, h * hourPx), 1f)
                        }
                        drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), 1f)
                    }
                    .pointerInput(epoch, hourPx) {
                        detectTapGestures { offset ->
                            val minutes = ((offset.y / hourPx) * 60f).toInt().coerceIn(0, 1439)
                            slotClick(date, (minutes / 30) * 30)
                        }
                    }
            ) {
                val colAreaWidth = maxWidth
                packed.forEach { pe ->
                    EventBlock(
                        event = pe.occurrence,
                        hourHeight = hourHeight,
                        colWidth = colAreaWidth / pe.totalColumns,
                        column = pe.column,
                        onClick = { onEventClick(pe.occurrence.eventId, pe.occurrence.originalDate) }
                    )
                }
                if (epoch == todayEpoch) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .offset(y = hourHeight * (nowMinute / 60f))
                            .background(nowColor)
                    )
                }
            }
        }
    }
}
