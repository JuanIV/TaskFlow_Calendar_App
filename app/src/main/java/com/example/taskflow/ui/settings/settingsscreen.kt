package com.example.taskflow.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.taskflow.R
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.notifications.NotificationChannels
import com.example.taskflow.ui.components.CustomTimePickerDialog
import java.time.LocalDate

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun TimeRow(label: String, minute: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(formatMinute(minute)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionDropdown(label: String, valueText: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(
                stringResource(if (granted) R.string.s2_perm_granted else R.string.s2_perm_missing),
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onFix) { Text(stringResource(R.string.s2_perm_fix)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val dayStartHour by viewModel.dayStartHour.collectAsState()
    val overdueMode by viewModel.overdueMode.collectAsState()
    val todayMinute by viewModel.todayTasksMinute.collectAsState()
    val morningEnabled by viewModel.morningEnabled.collectAsState()
    val morningMinute by viewModel.morningMinute.collectAsState()
    val eveningEnabled by viewModel.eveningEnabled.collectAsState()
    val eveningMinute by viewModel.eveningMinute.collectAsState()
    val quietEnabled by viewModel.quietEnabled.collectAsState()
    val quietStart by viewModel.quietStart.collectAsState()
    val quietEnd by viewModel.quietEnd.collectAsState()
    val looseEnabled by viewModel.looseEnabled.collectAsState()
    val looseInterval by viewModel.looseInterval.collectAsState()
    val looseStart by viewModel.looseStart.collectAsState()
    val looseEnd by viewModel.looseEnd.collectAsState()

    var pickerTarget by remember { mutableStateOf<String?>(null) }
    var pickerMinute by remember { mutableStateOf(0) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    val errLoose = stringResource(R.string.s2_err_loose_window)
    val backupOk = stringResource(R.string.s2_backup_ok)
    val backupFail = stringResource(R.string.s2_backup_fail)
    val restoreOk = stringResource(R.string.s2_restore_ok)
    val restoreFail = stringResource(R.string.s2_restore_fail)

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            viewModel.exportData(uri) { ok ->
                Toast.makeText(context, if (ok) backupOk else backupFail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importData(uri) { ok ->
                if (ok) {
                    Toast.makeText(context, restoreOk, Toast.LENGTH_LONG).show()
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (launch != null) context.startActivity(launch)
                    Runtime.getRuntime().exit(0)
                } else {
                    Toast.makeText(context, restoreFail, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val notifGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val exactGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }
    val batteryGranted = remember(refreshTick) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openPicker(target: String, minute: Int) {
        pickerMinute = minute
        pickerTarget = target
    }

    val channels = listOf(
        Pair(NotificationChannels.EVENT_REMINDERS, R.string.s2_ch_event_name),
        Pair(NotificationChannels.TASK_REMINDERS, R.string.s2_ch_task_name),
        Pair(NotificationChannels.ROUTINE_ALERTS, R.string.s2_ch_alert_name),
        Pair(NotificationChannels.SUMMARIES, R.string.s2_ch_summary_name),
        Pair(NotificationChannels.PERSISTENT_TASKS, R.string.s2_ch_tasks_name),
        Pair(NotificationChannels.PERSISTENT_ROUTINES, R.string.s2_ch_routines_name),
        Pair(NotificationChannels.LOOSE_TASKS, R.string.s2_ch_loose_name)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.s2_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.s2_back)) }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            AppearanceSection()
            AlarmSection()
            LockSection()
            TransferSection()
            DeviceCalendarsSection()
            WeeklySummarySection()

            SectionTitle(stringResource(R.string.s2_set_general))
            OptionDropdown(
                label = stringResource(R.string.s2_set_day_start),
                valueText = String.format("%02d:00", dayStartHour),
                options = (0..6).map { Pair(it, String.format("%02d:00", it)) },
                onSelect = { viewModel.updateDayStartHour(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OptionDropdown(
                label = stringResource(R.string.s2_set_overdue),
                valueText = stringResource(if (overdueMode == "SHOW") R.string.s2_overdue_show else R.string.s2_overdue_move),
                options = listOf(
                    Pair("SHOW", stringResource(R.string.s2_overdue_show)),
                    Pair("MOVE_TO_TODAY", stringResource(R.string.s2_overdue_move))
                ),
                onSelect = { viewModel.updateOverdueMode(it) }
            )

            SectionTitle(stringResource(R.string.s2_set_notifications))
            TimeRow(stringResource(R.string.s2_set_today_time), todayMinute) { openPicker("TODAY", todayMinute) }
            SwitchRow(stringResource(R.string.s2_set_morning), morningEnabled) { viewModel.updateMorning(it, morningMinute) }
            if (morningEnabled) TimeRow(stringResource(R.string.s2_set_loose_from), morningMinute) { openPicker("MORNING", morningMinute) }
            SwitchRow(stringResource(R.string.s2_set_evening), eveningEnabled) { viewModel.updateEvening(it, eveningMinute) }
            if (eveningEnabled) TimeRow(stringResource(R.string.s2_set_loose_from), eveningMinute) { openPicker("EVENING", eveningMinute) }

            SwitchRow(stringResource(R.string.s2_set_quiet), quietEnabled) { viewModel.updateQuiet(it, quietStart, quietEnd) }
            if (quietEnabled) {
                TimeRow(stringResource(R.string.s2_set_quiet_from), quietStart) { openPicker("QUIET_START", quietStart) }
                TimeRow(stringResource(R.string.s2_set_quiet_to), quietEnd) { openPicker("QUIET_END", quietEnd) }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.s2_set_loose), looseEnabled) { viewModel.updateLoose(it, looseInterval, looseStart, looseEnd) }
            if (looseEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OptionDropdown(
                    label = stringResource(R.string.s2_set_loose_every),
                    valueText = stringResource(R.string.s2_every_hours, looseInterval),
                    options = listOf(1, 2, 3, 4, 6, 8, 12, 24).map { Pair(it, stringResource(R.string.s2_every_hours, it)) },
                    onSelect = { viewModel.updateLoose(true, it, looseStart, looseEnd) }
                )
                TimeRow(stringResource(R.string.s2_set_loose_from), looseStart) { openPicker("LOOSE_START", looseStart) }
                TimeRow(stringResource(R.string.s2_set_loose_to), looseEnd) { openPicker("LOOSE_END", looseEnd) }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.s2_set_channels), style = MaterialTheme.typography.bodyLarge)
            channels.forEach { (channelId, nameRes) ->
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    context.startActivity(intent)
                }) { Text(stringResource(nameRes)) }
            }

            SectionTitle(stringResource(R.string.s2_set_data))
            OutlinedButton(
                onClick = { exportLauncher.launch("taskflow_backup_" + LocalDate.now().toString() + ".db") },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.s2_set_backup_create)) }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showRestoreConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.s2_set_backup_restore), color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.s2_set_trash)) }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.s2_set_history)) }

            SectionTitle(stringResource(R.string.s2_set_permissions))
            PermissionRow(stringResource(R.string.s2_perm_notifications), notifGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PermissionRow(stringResource(R.string.s2_perm_exact), exactGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:" + context.packageName) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PermissionRow(stringResource(R.string.s2_perm_battery), batteryGranted) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.s2_restore_title)) },
            text = { Text(stringResource(R.string.s2_restore_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    importLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.s2_yes), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    val target = pickerTarget
    if (target != null) {
        val state = rememberTimePickerState(initialHour = pickerMinute / 60, initialMinute = pickerMinute % 60)
        CustomTimePickerDialog(
            state = state,
            onDismiss = { pickerTarget = null },
            onConfirm = {
                val m = state.hour * 60 + state.minute
                when (target) {
                    "TODAY" -> viewModel.updateTodayTasksMinute(m)
                    "MORNING" -> viewModel.updateMorning(morningEnabled, m)
                    "EVENING" -> viewModel.updateEvening(eveningEnabled, m)
                    "QUIET_START" -> viewModel.updateQuiet(quietEnabled, m, quietEnd)
                    "QUIET_END" -> viewModel.updateQuiet(quietEnabled, quietStart, m)
                    "LOOSE_START" -> if (m >= looseEnd) Toast.makeText(context, errLoose, Toast.LENGTH_SHORT).show()
                        else viewModel.updateLoose(looseEnabled, looseInterval, m, looseEnd)
                    "LOOSE_END" -> if (m <= looseStart) Toast.makeText(context, errLoose, Toast.LENGTH_SHORT).show()
                        else viewModel.updateLoose(looseEnabled, looseInterval, looseStart, m)
                }
                pickerTarget = null
            }
        )
    }
}
