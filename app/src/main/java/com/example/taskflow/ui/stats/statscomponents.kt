package com.example.taskflow.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskflow.R
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun StatsHeatmap(
    days: Map<Long, DayInfo>,
    todayEpoch: Long,
    baseColor: Color,
    onDayTap: (Long) -> Unit
) {
    val weeks = 26
    val cell = 14.dp
    val gap = 2.dp
    // Filas = lunes a domingo; la ultima columna es la semana actual
    val gridStart = remember(todayEpoch) {
        LocalDate.ofEpochDay(todayEpoch).with(DayOfWeek.MONDAY).toEpochDay() - (weeks - 1) * 7L
    }
    val errorColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    val graceColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    val pausedColor = Color.Gray.copy(alpha = 0.4f)
    val emptyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val tap by rememberUpdatedState(onDayTap)

    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(
            modifier = Modifier
                .width((cell + gap) * weeks - gap)
                .height((cell + gap) * 7 - gap)
                .pointerInput(todayEpoch, gridStart) {
                    detectTapGestures { offset ->
                        val step = (cell + gap).toPx()
                        val col = (offset.x / step).toInt()
                        val row = (offset.y / step).toInt()
                        if (col in 0 until weeks && row in 0..6) {
                            val epoch = gridStart + col * 7L + row
                            if (epoch in 0L..todayEpoch) tap(epoch)
                        }
                    }
                }
        ) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            for (col in 0 until weeks) {
                for (row in 0..6) {
                    val epoch = gridStart + col * 7L + row
                    if (epoch > todayEpoch) continue
                    val color = when (days[epoch]?.status) {
                        "COMPLETED" -> baseColor
                        "FAILED" -> errorColor
                        "GRACE" -> graceColor
                        "PAUSED" -> pausedColor
                        else -> emptyColor
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx),
                        cornerRadius = radius
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsLegend(baseColor: Color) {
    val items = listOf(
        Pair(baseColor, R.string.s2_stats_completed),
        Pair(MaterialTheme.colorScheme.error.copy(alpha = 0.6f), R.string.s2_stats_failed),
        Pair(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f), R.string.s2_stats_grace),
        Pair(Color.Gray.copy(alpha = 0.4f), R.string.s2_stats_paused),
        Pair(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), R.string.s2_stats_none)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (color, labelRes) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(labelRes), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StatsWeekBars(weeks: List<Pair<String, Float>>, color: Color) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weeks.forEach { (label, percent) ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(percent.toInt().toString() + "%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(track, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((percent / 100f).coerceIn(0f, 1f))
                            .background(color, RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, fontSize = 10.sp)
            }
        }
    }
}
