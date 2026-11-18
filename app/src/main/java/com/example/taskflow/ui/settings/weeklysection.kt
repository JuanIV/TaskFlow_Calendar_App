package com.example.taskflow.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.R
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.scheduling.SchedulingCoordinator
import com.example.taskflow.ui.components.CustomTimePickerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeeklySettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val coordinator: SchedulingCoordinator
) : ViewModel() {
    val enabled: StateFlow<Boolean> = repo.weeklyEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val day: StateFlow<Int> = repo.weeklyDayFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 7)
    val minute: StateFlow<Int> = repo.weeklyMinuteFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 20 * 60)

    fun update(enabled: Boolean, day: Int, minute: Int) {
        viewModelScope.launch {
            repo.setWeekly(enabled, day, minute)
            coordinator.rescheduleAll()
        }
    }
}

@Composable
private fun weeklyDayLabel(day: Int): String {
    val res = listOf(
        R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
        R.string.day_fri, R.string.day_sat, R.string.day_sun
    )
    return stringResource(res[(day - 1).coerceIn(0, 6)]).replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklySummarySection(viewModel: WeeklySettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsState()
    val day by viewModel.day.collectAsState()
    val minute by viewModel.minute.collectAsState()
    var dayExpanded by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.s3_set_weekly),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.s3_weekly_enable), modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { viewModel.update(it, day, minute) })
    }
    if (enabled) {
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = it }) {
            OutlinedTextField(
                value = weeklyDayLabel(day),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.s3_weekly_day)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                (1..7).forEach { d ->
                    DropdownMenuItem(
                        text = { Text(weeklyDayLabel(d)) },
                        onClick = { viewModel.update(enabled, d, minute); dayExpanded = false }
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.s3_weekly_time), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showPicker = true }) { Text(formatMinute(minute)) }
        }
    }
    if (showPicker) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { showPicker = false },
            onConfirm = {
                viewModel.update(enabled, day, state.hour * 60 + state.minute)
                showPicker = false
            }
        )
    }
}
