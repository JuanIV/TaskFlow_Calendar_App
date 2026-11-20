package com.example.taskflow.widgets

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.taskflow.R
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.ui.calendar.weekStartOf
import com.example.taskflow.ui.calendar.weekdayHeaderRes
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private data class WeekDayInfo(
    val epoch: Long,
    val letter: String,
    val label: String,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasItems: Boolean
)

private data class WeekData(val title: String, val days: List<WeekDayInfo>, val selectedEpoch: Long, val items: List<DayItem>)

private suspend fun loadWeek(context: Context, weekOffset: Int, selectedPref: Long): WeekData {
    val entry = widgetEntryPoint(context)
    val todayEpoch = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val first = DayOfWeek.of(entry.settingsRepo().firstDayFlow.first().coerceIn(1, 7))
    val weekStart = weekStartOf(LocalDate.ofEpochDay(todayEpoch).plusWeeks(weekOffset.toLong()), first)
    val from = weekStart.toEpochDay()
    val to = from + 6
    val itemsByDay = loadDayItems(context, from, to, true, true, null)
    val selected = when {
        selectedPref in from..to -> selectedPref
        todayEpoch in from..to -> todayEpoch
        else -> from
    }
    val letters = weekdayHeaderRes(first).map { context.getString(it) }
    val days = (0..6).map { i ->
        val epoch = from + i
        WeekDayInfo(
            epoch = epoch,
            letter = letters[i],
            label = LocalDate.ofEpochDay(epoch).dayOfMonth.toString(),
            isToday = epoch == todayEpoch,
            isSelected = epoch == selected,
            hasItems = !(itemsByDay[epoch].isNullOrEmpty())
        )
    }
    val mid = weekStart.plusDays(3)
    val monthName = mid.format(DateTimeFormatter.ofPattern("MMMM", Locale("es", "ES"))).replaceFirstChar { it.uppercase() }
    val weekNumber = mid.get(WeekFields.ISO.weekOfWeekBasedYear())
    return WeekData(
        title = context.getString(R.string.s4_widget_week_title, monthName, weekNumber),
        days = days,
        selectedEpoch = selected,
        items = itemsByDay[selected] ?: emptyList()
    )
}

@Composable
private fun RowScope.WeekDayButton(day: WeekDayInfo) {
    val numberBase = GlanceModifier.size(30.dp)
    val numberModifier = if (day.isSelected) numberBase.background(GlanceTheme.colors.primaryContainer).cornerRadius(15.dp) else numberBase
    val numberColor = when {
        day.isSelected -> GlanceTheme.colors.onPrimaryContainer
        day.isToday -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.onBackground
    }
    Column(
        modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<SelectDayCallback>(actionParametersOf(WidgetKeys.Epoch to day.epoch))),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = day.letter, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center))
        Box(modifier = numberModifier, contentAlignment = Alignment.Center) {
            Text(
                text = day.label,
                style = TextStyle(
                    color = numberColor,
                    fontSize = 14.sp,
                    fontWeight = if (day.isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            )
        }
        Text(
            text = if (day.hasItems) "\u2022" else " ",
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 10.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun WeekItemRow(context: Context, item: DayItem) {
    if (item.isTask) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CheckBox(
                checked = item.completed,
                onCheckedChange = actionRunCallback<ToggleWidgetTaskCallback>(actionParametersOf(WidgetKeys.TaskId to item.taskId)),
                text = item.text,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 13.sp,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(text = item.timeText, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        }
    } else {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(widgetOpenIntent(context, item.route))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = GlanceModifier.width(4.dp).height(20.dp).background(ColorProvider(Color(item.colorArgb)))) { }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = item.text,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(text = item.timeText, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        }
    }
}

class WeekWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntryPoint(context)
        val p0 = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadWeek(context, p0[WidgetPrefs.WEEK_OFFSET] ?: 0, p0[WidgetPrefs.SELECTED_DAY] ?: -1L)
        val emptyText = context.getString(R.string.s4_widget_week_empty)

        provideContent {
            val prefs = currentState<Preferences>()
            val offset = prefs[WidgetPrefs.WEEK_OFFSET] ?: 0
            val selectedPref = prefs[WidgetPrefs.SELECTED_DAY] ?: -1L
            val tick = prefs[WidgetPrefs.REFRESH_TICK] ?: 0
            val tasksTrigger by entry.taskDao().getAllActiveTasks().collectAsState(initial = emptyList())
            val eventsTrigger by entry.eventDao().getActiveEventsFrom(0L).collectAsState(initial = emptyList())
            var data by remember { mutableStateOf(initial) }
            LaunchedEffect(offset, selectedPref, tick, tasksTrigger, eventsTrigger) {
                data = loadWeek(context, offset, selectedPref)
            }

            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp)) {
                    WidgetHeader(
                        title = data.title,
                        buttons = listOf(
                            HeaderButton(R.drawable.ic_widget_today, actionRunCallback<ShiftWeekCallback>(actionParametersOf(WidgetKeys.Delta to 0))),
                            HeaderButton(R.drawable.ic_widget_prev, actionRunCallback<ShiftWeekCallback>(actionParametersOf(WidgetKeys.Delta to -1))),
                            HeaderButton(R.drawable.ic_widget_next, actionRunCallback<ShiftWeekCallback>(actionParametersOf(WidgetKeys.Delta to 1))),
                            HeaderButton(R.drawable.ic_widget_refresh, actionRunCallback<RefreshWidgetCallback>()),
                            HeaderButton(R.drawable.ic_widget_add, actionStartActivity(widgetDayIntent(context, data.selectedEpoch, "weekadd")))
                        )
                    )
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                        data.days.forEach { day -> WeekDayButton(day) }
                    }
                    if (data.items.isEmpty()) {
                        Text(
                            text = emptyText,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                            modifier = GlanceModifier.fillMaxWidth().padding(12.dp)
                        )
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            items(data.items) { item -> WeekItemRow(context, item) }
                        }
                    }
                }
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}