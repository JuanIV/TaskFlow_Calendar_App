package com.example.taskflow.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.taskflow.devicecalendar.DeviceCalendar
import com.example.taskflow.devicecalendar.ExternalCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceCalendarsViewModel @Inject constructor(
    private val repo: ExternalCalendarRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settingsRepo.extCalendarEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val selectedIds: StateFlow<Set<Long>> = settingsRepo.extCalendarIdsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _calendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val calendars: StateFlow<List<DeviceCalendar>> = _calendars.asStateFlow()

    fun hasPermission(): Boolean = repo.hasPermission()

    fun refresh() {
        viewModelScope.launch { _calendars.value = repo.listCalendars() }
    }

    fun enable() {
        viewModelScope.launch {
            val list = repo.listCalendars()
            _calendars.value = list
            val current = settingsRepo.extCalendarIdsFlow.first()
            val ids = if (current.isEmpty()) list.filter { it.visible }.map { it.id }.toSet() else current
            settingsRepo.setExtCalendars(true, ids)
        }
    }

    fun disable() {
        viewModelScope.launch {
            settingsRepo.setExtCalendars(false, settingsRepo.extCalendarIdsFlow.first())
        }
    }

    fun toggle(id: Long) {
        viewModelScope.launch {
            val current = settingsRepo.extCalendarIdsFlow.first()
            val next = if (id in current) current - id else current + id
            settingsRepo.setExtCalendars(enabled.value, next)
        }
    }
}

@Composable
fun DeviceCalendarsSection(viewModel: DeviceCalendarsViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsState()
    val selected by viewModel.selectedIds.collectAsState()
    val calendars by viewModel.calendars.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.enable()
    }

    LaunchedEffect(enabled) {
        if (enabled) viewModel.refresh()
    }

    Text(
        stringResource(R.string.s3_set_devcal),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.s3_devcal_enable), modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                if (!on) {
                    viewModel.disable()
                } else if (viewModel.hasPermission()) {
                    viewModel.enable()
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            }
        )
    }
    if (enabled) {
        Text(
            stringResource(R.string.s3_devcal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (calendars.isEmpty()) {
            Text(
                stringResource(R.string.s3_devcal_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        calendars.forEach { calendar ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.toggle(calendar.id) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = calendar.id in selected, onCheckedChange = { viewModel.toggle(calendar.id) })
                Column {
                    Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        calendar.account,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
