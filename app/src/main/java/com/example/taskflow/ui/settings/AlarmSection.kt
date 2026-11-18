package com.example.taskflow.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.R
import com.example.taskflow.data.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmSettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {
    val snooze: StateFlow<Int> = repo.alarmSnoozeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 10)

    fun setSnooze(minutes: Int) {
        viewModelScope.launch { repo.setAlarmSnooze(minutes) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSection(viewModel: AlarmSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snooze by viewModel.snooze.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.s3_set_alarms),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(R.string.s3_alarm_minutes, snooze),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.s3_alarm_snooze_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(5, 10, 15, 30).forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.s3_alarm_minutes, minutes)) },
                    onClick = { viewModel.setSnooze(minutes); expanded = false }
                )
            }
        }
    }

    if (Build.VERSION.SDK_INT >= 34) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.canUseFullScreenIntent()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.s3_alarm_fullscreen_perm)) }
        }
    }
}
