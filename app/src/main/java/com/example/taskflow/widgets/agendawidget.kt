package com.example.taskflow.widgets

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.expandEvents
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.domain.isVisibleIn
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.ui.calendar.groupByDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface AgendaRow
private data class AgendaHeader(val text: String) : AgendaRow
private data class AgendaEventRow(val text: String, val colorArgb: Int, val route: String) : AgendaRow
private data class AgendaTaskRow(val id: Long, val title: String, val completed: Boolean) : AgendaRow
private data class AgendaData(val rows: List<AgendaRow>, val today: Long)

private suspend fun loadAgenda(context: Context, days: Int, includeTasks: Boolean): AgendaData {
    val entry = widgetEntryPoint(context)
    val today = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val last = today + (days.coerceIn(1, 31) - 1)

    val events = entry.eventDao().getActiveEventsFromSync(today - 365)
    val exceptions = if (events.isEmpty()) emptyList() else entry.exceptionDao().getExceptionsForEvents(events.map { it.id })
    val byDay = groupByDay(expandEvents(events, exceptions, today, last).filter { it.isVisibleIn("AGENDA") })
    val tasksByDay: Map<Long, List<TaskEntity>> = if (includeTasks) {
        entry.taskDao().getTasksUpToDateSync(last)
            .filter { val d = it.date; d != null && d >= today }
            .groupBy { it.date ?: 0L }
    } else {
        emptyMap()
    }

    val format = DateTimeFormatter.ofPattern("EEEE d MMM", Locale("es", "ES"))
    val rows = mutableListOf<AgendaRow>()
    for (day in today..last) {
        val dayEvents = byDay[day] ?: emptyList()
        val dayTasks = tasksByDay[day] ?: emptyList()
        if (dayEvents.isEmpty() && dayTasks.isEmpty()) continue
        rows.add(AgendaHeader(LocalDate.ofEpochDay(day).format(format).replaceFirstChar { it.uppercase() }))
        for (event in dayEvents) {
            val start = event.startMinute
            val time = if (event.allDay || start == null) context.getString(R.string.s2_all_day)
            else formatMinute(start)
            rows.add(AgendaEventRow(time + "  " + event.title, event.colorArgb, "event_detail/" + event.eventId + "/" + event.originalDate))
        }
        for (task in dayTasks) rows.add(AgendaTaskRow(task.id, task.title, task.isCompleted))
    }
    return AgendaData(rows, today)
}

class AgendaWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntryPoint(context)
        val p0 = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadAgenda(context, p0[WidgetPrefs.AGENDA_DAYS] ?: 7, p0[WidgetPrefs.AGENDA_TASKS] ?: true)
        val emptyText = context.getString(R.string.s2_widget_nothing)
        val title = context.getString(R.string.s2_view_agenda)
        val calendarIntent = widgetOpenIntent(context, "calendar")

        provideContent {
            val prefs = currentState<Preferences>()
            val days = prefs[WidgetPrefs.AGENDA_DAYS] ?: 7
            val includeTasks = prefs[WidgetPrefs.AGENDA_TASKS] ?: true
            val tick = prefs[WidgetPrefs.REFRESH_TICK] ?: 0
            val tasksTrigger by entry.taskDao().getAllActiveTasks().collectAsState(initial = emptyList())
            val eventsTrigger by entry.eventDao().getActiveEventsFrom(0L).collectAsState(initial = emptyList())
            var data by remember { mutableStateOf(initial) }
            LaunchedEffect(days, includeTasks, tick, tasksTrigger, eventsTrigger) {
                data = loadAgenda(context, days, includeTasks)
            }

            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp)) {
                    WidgetHeader(
                        title = title,
                        buttons = listOf(
                            HeaderButton(R.drawable.ic_widget_refresh, actionRunCallback<RefreshWidgetCallback>()),
                            HeaderButton(R.drawable.ic_widget_add, actionStartActivity(widgetQuickAddIntent(context, data.today)))
                        )
                    )
                    if (data.rows.isEmpty()) {
                        Text(
                            text = emptyText,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp),
                            modifier = GlanceModifier.fillMaxWidth().padding(12.dp).clickable(actionStartActivity(calendarIntent))
                        )
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                            items(data.rows) { row ->
                                when (row) {
                                    is AgendaHeader -> Text(
                                        text = row.text,
                                        style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
                                    )
                                    is AgendaEventRow -> Row(
                                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(
                                            actionStartActivity(widgetOpenIntent(context, row.route))
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = GlanceModifier.width(4.dp).height(18.dp).background(ColorProvider(Color(row.colorArgb)))) { }
                                        Spacer(modifier = GlanceModifier.width(6.dp))
                                        Text(text = row.text, style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp))
                                    }
                                    is AgendaTaskRow -> CheckBox(
                                        checked = row.completed,
                                        onCheckedChange = actionRunCallback<ToggleWidgetTaskCallback>(actionParametersOf(WidgetKeys.TaskId to row.id)),
                                        text = row.title,
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

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}