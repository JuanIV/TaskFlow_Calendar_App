package com.example.taskflow.ui.stats

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    onNavigateToDetail: (Long) -> Unit = {},
    onOpenAchievements: () -> Unit = {}
) {
    val cards by viewModel.cards.collectAsState()
    val context = LocalContext.current
    val debuggable = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSeed by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_stats)) },
                actions = {
                    IconButton(onClick = onOpenAchievements) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = stringResource(R.string.s3_ach_title))
                    }
                    if (debuggable) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_menu))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.s2_stats_seed)) }, onClick = { menuExpanded = false; showSeed = true })
                                DropdownMenuItem(text = { Text(stringResource(R.string.s2_stats_clear)) }, onClick = { menuExpanded = false; showClear = true })
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards, key = { it.routineId }) { card ->
                StatCardView(card) { onNavigateToDetail(card.routineId) }
            }
        }
    }

    if (showSeed) {
        AlertDialog(
            onDismissRequest = { showSeed = false },
            title = { Text(stringResource(R.string.s2_stats_seed)) },
            text = { Text(stringResource(R.string.s2_stats_seed_msg)) },
            confirmButton = { TextButton(onClick = { viewModel.seedSampleData(); showSeed = false }) { Text(stringResource(R.string.s2_yes)) } },
            dismissButton = { TextButton(onClick = { showSeed = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.s2_stats_clear)) },
            text = { Text(stringResource(R.string.s2_stats_clear_msg)) },
            confirmButton = { TextButton(onClick = { viewModel.clearHistory(); showClear = false }) { Text(stringResource(R.string.s2_yes)) } },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatCardView(card: StatCard, onClick: () -> Unit) {
    val isGlobal = card.routineId == -1L
    val unitRes = if (isGlobal) R.string.s2_stats_days else R.string.s2_stats_times
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlobal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(card.name ?: stringResource(R.string.s2_stats_global), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatNumber(stringResource(R.string.s2_stats_current), stringResource(unitRes, card.current))
                StatNumber(stringResource(R.string.s2_stats_best), stringResource(unitRes, card.best))
                StatNumber(stringResource(R.string.s2_stats_compliance_30), card.percent30.toString() + "%")
            }
        }
    }
}

@Composable
private fun StatNumber(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
