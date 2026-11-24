package com.example.taskflow.widgets

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.projectTodayItems
import com.example.taskflow.domain.ProjectTodayItem
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TodayRow(val id: Long, val label: String, val completed: Boolean, val projectNodeId: Long?, val projectMinutes: Int, val projectId: Long?)
private data class TodayData(val header: String, val today: Long, val pending: Int, val rows: List<TodayRow>)

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

    val repo = entry.projectRepository()
    val projectRows = ArrayList<TodayRow>()
    for ((project, plan) in repo.getActiveProjectPlansSync(today)) {
        val titles = repo.getNodeTitles(project.id)
        for (item in projectTodayItems(project.id, project.name, plan, titles, today)) {
            projectRows.add(TodayRow(-1L, item.label, false, item.nodeId, item.minutes, project.id))
        }
    }

    val rows = (visible.take(maxRows).map { task ->
        val subs = entry.taskDao().getSubtasksForTaskSync(task.id)
        val counter = if (subs.isNotEmpty()) " (" + subs.count { it.isCompleted } + "/" + subs.size + ")" else ""
        val d = task.date
        val prefix = if (d != null && d < today) context.getString(R.string.s2_overdue_prefix) else ""
        TodayRow(task.id, prefix + task.title + counter, task.isCompleted, null, 0, null)
    } + projectRows).take(maxRows + projectRows.size)

    val header = LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "ES")))
        .replaceFirstChar { it.uppercase() }
    return TodayData(header, today, visible.count { !it.isCompleted } + projectRows.size, rows)
}

class TodayTasksWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntryPoint(context)
        val p0 = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadTodayData(
            context,
            p0[WidgetPrefs.TODAY_SHOW_DONE] ?: true,
            p0[WidgetPrefs.TODAY_SHOW_OVERDUE] ?: true,
            p0[WidgetPrefs.TODAY_MAX] ?: 20
        )
        val emptyText = context.getString(R.string.s2_widget_no_tasks)

        provideContent {
            val prefs = currentState<Preferences>()
            val showDone = prefs[WidgetPrefs.TODAY_SHOW_DONE] ?: true
            val showOverdue = prefs[WidgetPrefs.TODAY_SHOW_OVERDUE] ?: true
            val maxRows = prefs[WidgetPrefs.TODAY_MAX] ?: 20
            val tick = prefs[WidgetPrefs.REFRESH_TICK] ?: 0
            val tasksTrigger by entry.taskDao().getAllActiveTasks().collectAsState(initial = emptyList())
            var data by remember { mutableStateOf(initial) }
            LaunchedEffect(showDone, showOverdue, maxRows, tick, tasksTrigger) {
                data = loadTodayData(context, showDone, showOverdue, maxRows)
            }

            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp)) {
                    WidgetHeader(
                        title = data.header + "  \u00B7  " + data.pending,
                        buttons = listOf(
                            HeaderButton(R.drawable.ic_widget_refresh, actionRunCallback<RefreshWidgetCallback>()),
                            HeaderButton(R.drawable.ic_widget_add, actionStartActivity(widgetQuickAddIntent(context, data.today)))
                        )
                    )
                    if (data.rows.isEmpty()) {
                        Text(
                            text = emptyText,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp),
                            modifier = GlanceModifier.fillMaxWidth().padding(12.dp)
                        )
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                            items(data.rows) { row ->
                                val projectNode = row.projectNodeId
                                if (projectNode == null) {
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
                                } else {
                                    CheckBox(
                                        checked = false,
                                        onCheckedChange = actionRunCallback<CompleteProjectPartCallback>(
                                            actionParametersOf(
                                                WidgetKeys.TaskId to projectNode,
                                                WidgetKeys.Delta to row.projectMinutes
                                            )
                                        ),
                                        text = row.label,
                                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
                                    )
                                }
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
