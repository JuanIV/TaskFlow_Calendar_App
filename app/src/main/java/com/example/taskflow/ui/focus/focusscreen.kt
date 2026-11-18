package com.example.taskflow.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.ui.tasks.durationText
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
private fun phaseLabelText(phase: String): String = when (phase) {
    "SHORT_BREAK" -> stringResource(R.string.s3_focus_phase_short)
    "LONG_BREAK" -> stringResource(R.string.s3_focus_phase_long)
    "TIMER" -> stringResource(R.string.s3_focus_phase_timer)
    else -> stringResource(R.string.s3_focus_phase_focus)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    onBack: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val config by viewModel.config.collectAsState()
    val todayMinutes by viewModel.todayMinutes.collectAsState()

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) {
        while (state.active) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }

    var mode by remember { mutableStateOf("POMODORO") }
    var selectedTaskId by remember { mutableStateOf<Long?>(viewModel.initialTaskId.takeIf { it > 0L }) }
    var timerMinutes by remember { mutableStateOf(25) }
    var taskMenu by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var cfgFocus by remember(config) { mutableStateOf(config.focusMin.toString()) }
    var cfgShort by remember(config) { mutableStateOf(config.shortMin.toString()) }
    var cfgLong by remember(config) { mutableStateOf(config.longMin.toString()) }
    var cfgCycles by remember(config) { mutableStateOf(config.cycles.toString()) }

    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }
    LaunchedEffect(selectedTask?.id) {
        selectedTask?.estimatedMinutes?.let { timerMinutes = it.coerceIn(1, 600) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.s3_focus_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.active) {
                val remaining = (state.endMs - nowMs).coerceAtLeast(0L)
                val total = (state.endMs - state.startMs).coerceAtLeast(1L)
                val progress = (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val seconds = (remaining / 1000L).toInt()
                val clock = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
                val activeTask = tasks.firstOrNull { it.id == state.taskId }

                Spacer(modifier = Modifier.height(16.dp))
                Text(phaseLabelText(state.phase), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (activeTask != null) {
                    Text(activeTask.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp)
                    Text(clock, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                }
                if (state.mode == "POMODORO") {
                    Spacer(modifier = Modifier.height(16.dp))
                    val cycles = config.cycles.coerceAtLeast(1)
                    Text(stringResource(R.string.s3_focus_cycle, (state.cycle % cycles) + 1, cycles))
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { viewModel.stop() }) { Text(stringResource(R.string.s3_focus_stop)) }
                    if (state.mode == "POMODORO") {
                        Button(onClick = { viewModel.skip() }) { Text(stringResource(R.string.s3_focus_skip)) }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "POMODORO", onClick = { mode = "POMODORO" }, label = { Text(stringResource(R.string.s3_focus_mode_pomodoro)) })
                    FilterChip(selected = mode == "TIMER", onClick = { mode = "TIMER" }, label = { Text(stringResource(R.string.s3_focus_mode_timer)) })
                }
                Spacer(modifier = Modifier.height(16.dp))
                ExposedDropdownMenuBox(expanded = taskMenu, onExpandedChange = { taskMenu = it }) {
                    OutlinedTextField(
                        value = selectedTask?.title ?: stringResource(R.string.s3_focus_no_task),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.s3_focus_task)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = taskMenu, onDismissRequest = { taskMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.s3_focus_no_task)) },
                            onClick = { selectedTaskId = null; taskMenu = false }
                        )
                        tasks.forEach { task ->
                            DropdownMenuItem(
                                text = { Text(task.title) },
                                onClick = { selectedTaskId = task.id; taskMenu = false }
                            )
                        }
                    }
                }
                if (mode == "TIMER") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.s3_focus_minutes), modifier = Modifier.fillMaxWidth())
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 25, 30, 45, 60, 90).forEach { minutes ->
                            FilterChip(selected = timerMinutes == minutes, onClick = { timerMinutes = minutes }, label = { Text(minutes.toString()) })
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.s3_focus_phase_focus) + ": " + config.focusMin + " min",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.start(mode, selectedTaskId, timerMinutes) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.s3_focus_start_btn)) }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = { showConfig = !showConfig }) { Text(stringResource(R.string.s3_focus_config)) }
                if (showConfig) {
                    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)
                    OutlinedTextField(value = cfgFocus, onValueChange = { cfgFocus = it }, label = { Text(stringResource(R.string.s3_focus_cfg_focus)) }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cfgShort, onValueChange = { cfgShort = it }, label = { Text(stringResource(R.string.s3_focus_cfg_short)) }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cfgLong, onValueChange = { cfgLong = it }, label = { Text(stringResource(R.string.s3_focus_cfg_long)) }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cfgCycles, onValueChange = { cfgCycles = it }, label = { Text(stringResource(R.string.s3_focus_cfg_cycles)) }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.saveConfig(
                                cfgFocus.toIntOrNull() ?: config.focusMin,
                                cfgShort.toIntOrNull() ?: config.shortMin,
                                cfgLong.toIntOrNull() ?: config.longMin,
                                cfgCycles.toIntOrNull() ?: config.cycles
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.s3_focus_cfg_save)) }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                stringResource(R.string.s3_focus_today, durationText(todayMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
