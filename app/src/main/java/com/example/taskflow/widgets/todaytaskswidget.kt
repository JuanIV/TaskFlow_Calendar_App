package com.example.taskflow.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.logicalDateNow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TodayRow(val id: Long, val label: String, val completed: Boolean)
private data class TodayData(val header: String, val pending: Int, val rows: List<TodayRow>)

private suspend fun loadTodayData(context: Context, showDone: Boolean, showOverdue: Boolean, maxRows: Int): TodayData {
    val entry = widgetEntryPoint(context)
    val today = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val overdueMode = entry.settingsRepo().overdueModeFlow.first()
    val visible = entry.taskDao().getTasksUpToDateSync(today).filter { task ->
        val d = task.date
        val isToday = d == today
        val isOverdue = overdueMode == "SHOW" && showOverdue && d != null && d < today && !task.isCompleted
        (isToday || isOverdue) && (showDone || !task.isCompleted)
    }.sortedWith(compareBy<TaskEntity> { it.isCompleted }.thenByDescending { it.priority }.thenBy { it.sortOrder })

    val rows = visible.take(maxRows).map { task ->
        val subs = entry.taskDao().getSubtasksForTaskSync(task.id)
        val counter = if (subs.isNotEmpty()) " (" + subs.count { it.isCompleted } + "/" + subs.size + ")" else ""
        val d = task.date
        val prefix = if (d != null && d < today) context.getString(R.string.s2_overdue_prefix) else ""
        TodayRow(task.id, prefix + task.title + counter, task.isCompleted)
    }
    val header = LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "ES")))
        .replaceFirstChar { it.uppercase() }
    return TodayData(header, visible.count { !it.isCompleted }, rows)
}

class TodayTasksWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val data = loadTodayData(
            context,
            prefs[WidgetPrefs.TODAY_SHOW_DONE] ?: true,
            prefs[WidgetPrefs.TODAY_SHOW_OVERDUE] ?: true,
            prefs[WidgetPrefs.TODAY_MAX] ?: 20
        )
        val emptyText = context.getString(R.string.s2_widget_no_tasks)
        val openIntent = widgetOpenIntent(context, "tasks")
        provideContent {
            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).padding(8.dp)) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openIntent)).padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = data.header,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            text = data.pending.toString(),
                            style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        )
                    }
                    if (data.rows.isEmpty()) {
                        Text(text = emptyText, style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp))
                    } else {
                        LazyColumn {
                            items(data.rows) { row ->
                                CheckBox(
                                    checked = row.completed,
                                    onCheckedChange = actionRunCallback<ToggleWidgetTaskCallback>(actionParametersOf(WidgetKeys.TaskId to row.id)),
                                    text = row.label,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onBackground,
                                        fontSize = 13.sp,
                                        textDecoration = if (row.completed) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}