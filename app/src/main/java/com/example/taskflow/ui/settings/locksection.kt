package com.example.taskflow.ui.settings

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.example.taskflow.lock.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockSettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val lockState: LockState
) : ViewModel() {
    val enabled: StateFlow<Boolean> = repo.lockEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val timeout: StateFlow<Int> = repo.lockTimeoutFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            lockState.unlock()
            repo.setLock(value, timeout.value)
        }
    }

    fun setTimeout(sec: Int) {
        viewModelScope.launch { repo.setLock(enabled.value, sec) }
    }
}

@Composable
private fun lockTimeoutLabel(sec: Int): String = when (sec) {
    0 -> stringResource(R.string.s3_lock_t0)
    30 -> stringResource(R.string.s3_lock_t30)
    60 -> stringResource(R.string.s3_lock_t60)
    else -> stringResource(R.string.s3_lock_t300)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockSection(viewModel: LockSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val enabled by viewModel.enabled.collectAsState()
    val timeout by viewModel.timeout.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val secure = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    Text(
        stringResource(R.string.s3_set_lock),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.s3_lock_enable), modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            enabled = secure || enabled,
            onCheckedChange = { viewModel.setEnabled(it) }
        )
    }
    if (!secure && !enabled) {
        Text(
            stringResource(R.string.s3_lock_no_secure),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (enabled) {
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = lockTimeoutLabel(timeout),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.s3_lock_timeout)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(0, 30, 60, 300).forEach { sec ->
                    DropdownMenuItem(
                        text = { Text(lockTimeoutLabel(sec)) },
                        onClick = { viewModel.setTimeout(sec); expanded = false }
                    )
                }
            }
        }
    }
}