package com.example.taskflow.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.taskflow.R
import com.example.taskflow.ui.theme.TaskFlowTheme
import com.example.taskflow.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className ?: ""
        val kind = when {
            provider.endsWith("TodayTasksWidgetReceiver") -> "TODAY"
            provider.endsWith("AgendaWidgetReceiver") -> "AGENDA"
            provider.endsWith("RoutinesWidgetReceiver") -> "ROUTINES"
            provider.endsWith("StreakWidgetReceiver") -> "STREAK"
            provider.endsWith("FocusWidgetReceiver") -> "FOCUS"
            provider.endsWith("MonthWidgetReceiver") -> "MONTH"
            else -> ""
        }
        if (kind.isEmpty()) {
            finish()
            return
        }

        setContent {
            val themeMode by themeViewModel.themeMode.collectAsState()
            val dynamic by themeViewModel.dynamicColor.collectAsState()
            val accent by themeViewModel.accent.collectAsState()
            var showDone by remember { mutableStateOf(true) }
            var showOverdue by remember { mutableStateOf(true) }
            var maxRows by remember { mutableStateOf(20) }
            var days by remember { mutableStateOf(7) }
            var agendaTasks by remember { mutableStateOf(true) }
            var onlyPending by remember { mutableStateOf(false) }
            var streakBest by remember { mutableStateOf(true) }
            var focusTotal by remember { mutableStateOf(false) }
            var monthEvents by remember { mutableStateOf(true) }
            var monthTasks by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                try {
                    val id = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                    val p = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, id)
                    showDone = p[WidgetPrefs.TODAY_SHOW_DONE] ?: true
                    showOverdue = p[WidgetPrefs.TODAY_SHOW_OVERDUE] ?: true
                    maxRows = p[WidgetPrefs.TODAY_MAX] ?: 20
                    days = p[WidgetPrefs.AGENDA_DAYS] ?: 7
                    agendaTasks = p[WidgetPrefs.AGENDA_TASKS] ?: true
                    onlyPending = p[WidgetPrefs.ROUTINES_ONLY_PENDING] ?: false
                    streakBest = p[WidgetPrefs.STREAK_SHOW_BEST] ?: true
                    focusTotal = p[WidgetPrefs.FOCUS_SHOW_TOTAL] ?: false
                    monthEvents = p[WidgetPrefs.MONTH_MARK_EVENTS] ?: true
                    monthTasks = p[WidgetPrefs.MONTH_MARK_TASKS] ?: true
                } catch (e: Exception) {
                    // widget nuevo: se usan los valores por defecto
                }
            }

            TaskFlowTheme(themeMode = themeMode, dynamicColor = dynamic, accentArgb = accent) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
                    ) {
                        Text(
                            stringResource(R.string.s3_wcfg_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (kind == "TODAY") {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_today_done), modifier = Modifier.weight(1f))
                                Switch(checked = showDone, onCheckedChange = { showDone = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_today_overdue), modifier = Modifier.weight(1f))
                                Switch(checked = showOverdue, onCheckedChange = { showOverdue = it })
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.s3_wcfg_max))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(5, 10, 20).forEach { n ->
                                    FilterChip(
                                        selected = maxRows == n,
                                        onClick = { maxRows = n },
                                        label = { Text(stringResource(R.string.s3_wcfg_number, n)) }
                                    )
                                }
                            }
                        }

                        if (kind == "AGENDA") {
                            Text(stringResource(R.string.s3_wcfg_days))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(3, 7, 14).forEach { n ->
                                    FilterChip(
                                        selected = days == n,
                                        onClick = { days = n },
                                        label = { Text(stringResource(R.string.s3_wcfg_number, n)) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_agenda_tasks), modifier = Modifier.weight(1f))
                                Switch(checked = agendaTasks, onCheckedChange = { agendaTasks = it })
                            }
                        }

                        if (kind == "ROUTINES") {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_only_pending), modifier = Modifier.weight(1f))
                                Switch(checked = onlyPending, onCheckedChange = { onlyPending = it })
                            }
                        }

                        if (kind == "STREAK") {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_streak_best), modifier = Modifier.weight(1f))
                                Switch(checked = streakBest, onCheckedChange = { streakBest = it })
                            }
                        }

                        if (kind == "FOCUS") {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_focus_total), modifier = Modifier.weight(1f))
                                Switch(checked = focusTotal, onCheckedChange = { focusTotal = it })
                            }
                        }

                        if (kind == "MONTH") {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_month_events), modifier = Modifier.weight(1f))
                                Switch(checked = monthEvents, onCheckedChange = { monthEvents = it })
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s3_wcfg_month_tasks), modifier = Modifier.weight(1f))
                                Switch(checked = monthTasks, onCheckedChange = { monthTasks = it })
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                save(
                                    kind, showDone, showOverdue, maxRows, days, agendaTasks, onlyPending,
                                    streakBest, focusTotal, monthEvents, monthTasks
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.s3_wcfg_save)) }
                    }
                }
            }
        }
    }

    private fun save(
        kind: String,
        showDone: Boolean,
        showOverdue: Boolean,
        maxRows: Int,
        days: Int,
        agendaTasks: Boolean,
        onlyPending: Boolean,
        streakBest: Boolean,
        focusTotal: Boolean,
        monthEvents: Boolean,
        monthTasks: Boolean
    ) {
        lifecycleScope.launch {
            try {
                val id = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, id) { prefs ->
                    val m = prefs.toMutablePreferences()
                    when (kind) {
                        "TODAY" -> {
                            m[WidgetPrefs.TODAY_SHOW_DONE] = showDone
                            m[WidgetPrefs.TODAY_SHOW_OVERDUE] = showOverdue
                            m[WidgetPrefs.TODAY_MAX] = maxRows
                        }
                        "AGENDA" -> {
                            m[WidgetPrefs.AGENDA_DAYS] = days
                            m[WidgetPrefs.AGENDA_TASKS] = agendaTasks
                        }
                        "ROUTINES" -> {
                            m[WidgetPrefs.ROUTINES_ONLY_PENDING] = onlyPending
                        }
                        "STREAK" -> {
                            m[WidgetPrefs.STREAK_SHOW_BEST] = streakBest
                        }
                        "FOCUS" -> {
                            m[WidgetPrefs.FOCUS_SHOW_TOTAL] = focusTotal
                        }
                        else -> {
                            m[WidgetPrefs.MONTH_MARK_EVENTS] = monthEvents
                            m[WidgetPrefs.MONTH_MARK_TASKS] = monthTasks
                        }
                    }
                    m
                }
                when (kind) {
                    "TODAY" -> TodayTasksWidget().update(this@WidgetConfigActivity, id)
                    "AGENDA" -> AgendaWidget().update(this@WidgetConfigActivity, id)
                    "ROUTINES" -> RoutinesWidget().update(this@WidgetConfigActivity, id)
                    "STREAK" -> StreakWidget().update(this@WidgetConfigActivity, id)
                    "FOCUS" -> FocusWidget().update(this@WidgetConfigActivity, id)
                    else -> MonthWidget().update(this@WidgetConfigActivity, id)
                }
            } catch (e: Exception) {
                // si falla el guardado, el widget conserva su configuracion anterior
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}