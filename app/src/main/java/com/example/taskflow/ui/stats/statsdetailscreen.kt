package com.example.taskflow.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.domain.DayStatus
import com.example.taskflow.domain.compliancePercent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailScreen(
    onBack: () -> Unit,
    viewModel: StatsDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    val baseColor = state.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val today = state.today
    val statuses = remember(state.days) { state.days.map { DayStatus(it.key, it.value.status) } }

    val bars = remember(statuses, today) {
        if (today <= 0L) emptyList() else {
            val currentMonday = LocalDate.ofEpochDay(today).with(DayOfWeek.MONDAY).toEpochDay()
            (7 downTo 0).map { i ->
                val monday = currentMonday - 7L * i
                val to = minOf(monday + 6, today - 1)
                val label = LocalDate.ofEpochDay(monday).format(DateTimeFormatter.ofPattern("d/M"))
                Pair(label, if (to < monday) 0f else compliancePercent(statuses, monday, to))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.routineName ?: stringResource(R.string.s2_stats_global)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (state.loaded && today > 0L) {
                Text(stringResource(R.string.s2_stats_heatmap_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                StatsHeatmap(days = state.days, todayEpoch = today, baseColor = baseColor, onDayTap = { selectedDay = it })
                Spacer(modifier = Modifier.height(12.dp))
                StatsLegend(baseColor)

                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.s2_stats_compliance_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(7 to R.string.s2_stats_7d, 30 to R.string.s2_stats_30d, 90 to R.string.s2_stats_90d).forEach { (n, labelRes) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                compliancePercent(statuses, today - n, today - 1).toInt().toString() + "%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.s2_stats_bars_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                if (state.days.isEmpty()) {
                    Text(stringResource(R.string.s2_stats_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                StatsWeekBars(weeks = bars, color = baseColor)
            }
        }
    }

    selectedDay?.let { epoch ->
        val info = state.days[epoch]
        val dateText = LocalDate.ofEpochDay(epoch).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "ES")))
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text(dateText) },
            text = {
                if (info == null) {
                    Text(stringResource(R.string.s2_stats_none))
                } else {
                    val statusText = when (info.status) {
                        "COMPLETED" -> stringResource(R.string.s2_stats_completed)
                        "FAILED" -> stringResource(R.string.s2_stats_failed)
                        "GRACE" -> stringResource(R.string.s2_stats_grace)
                        "PAUSED" -> stringResource(R.string.s2_stats_paused)
                        else -> stringResource(R.string.s2_stats_none)
                    }
                    Text(statusText + "\n" + stringResource(R.string.s2_stats_day_detail, info.done, info.required))
                }
            },
            confirmButton = { TextButton(onClick = { selectedDay = null }) { Text(stringResource(R.string.s2_close)) } }
        )
    }
}
