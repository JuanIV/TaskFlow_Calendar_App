package com.example.taskflow.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.achievements.achievementDescRes
import com.example.taskflow.achievements.achievementNameRes
import com.example.taskflow.ui.tasks.durationText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun descriptionOf(item: AchievementItem): String = when (item.metric) {
    "ROUTINES", "EVENTS" -> stringResource(achievementDescRes(item.metric))
    else -> stringResource(achievementDescRes(item.metric), item.target)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "ES")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.s3_ach_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "stats") {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.s3_ach_stat_tasks, ui.stats.tasksCompleted))
                        Text(stringResource(R.string.s3_ach_stat_streak, ui.stats.bestStreak))
                        Text(stringResource(R.string.s3_ach_stat_days, ui.stats.perfectDays))
                        Text(stringResource(R.string.s3_ach_stat_focus, durationText(ui.stats.focusMinutes)))
                    }
                }
            }
            items(ui.items, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = if (item.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(achievementNameRes(item.id)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                descriptionOf(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val at = item.unlockedAt
                            if (item.unlocked) {
                                if (at != null) {
                                    Text(
                                        stringResource(
                                            R.string.s3_ach_unlocked_on,
                                            Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                LinearProgressIndicator(
                                    progress = { (item.current.toFloat() / item.target.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                )
                                Text(
                                    stringResource(R.string.s3_ach_progress, item.current.coerceAtMost(item.target), item.target),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item(key = "spacer_end") { Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}
