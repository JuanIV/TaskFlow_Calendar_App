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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.taskflow.R
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.ui.calendar.getDaysInMonthGrid
import com.example.taskflow.ui.calendar.weekdayHeaderRes
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class MonthCell(val epoch: Long, val label: String, val inMonth: Boolean, val isToday: Boolean, val items: List<DayItem>)
private data class MonthData(val title: String, val todayEpoch: Long, val headers: List<String>, val weeks: List<List<MonthCell>>)

private suspend fun loadMonth(context: Context, monthOffset: Int, showEvents: Boolean, showTasks: Boolean): MonthData {
    val entry = widgetEntryPoint(context)
    val todayEpoch = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val todayDate = LocalDate.ofEpochDay(todayEpoch)
    val month = YearMonth.from(todayDate).plusMonths(monthOffset.toLong())
    val first = DayOfWeek.of(entry.settingsRepo().firstDayFlow.first().coerceIn(1, 7))
    val days = getDaysInMonthGrid(month, first)
    val from = days.first().toEpochDay()
    val to = days.last().toEpochDay()
    val itemsByDay = loadDayItems(context, from, to, showEvents, showTasks, "MONTH")

    val weeks = (0 until 6).map { w ->
        (0 until 7).map { c ->
            val d = days[w * 7 + c]
            val epoch = d.toEpochDay()
            MonthCell(epoch, d.dayOfMonth.toString(), d.month == month.month, epoch == todayEpoch, itemsByDay[epoch] ?: emptyList())
        }
    }
    val title = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))).replaceFirstChar { it.uppercase() }
    return MonthData(title, todayEpoch, weekdayHeaderRes(first).map { context.getString(it) }, weeks)
}

@Composable
private fun MonthItemChip(item: DayItem) {
    Box(modifier = GlanceModifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, bottom = 1.dp)) {
        if (item.isTask) {
            Text(
                text = item.text,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 9.sp,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                ),
                modifier = GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.primaryContainer).padding(horizontal = 2.dp)
            )
        } else {
            val bg = Color(item.colorArgb)
            val fg = if (bg.luminance() > 0.5f) Color.Black else Color.White
            Text(
                text = item.text,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(fg), fontSize = 9.sp),
                modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(bg)).padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun RowScope.MonthDayCell(cell: MonthCell, maxLines: Int, lineColor: ColorProvider, onClick: Action) {
    val total = cell.items.size
    val shown = if (total <= maxLines) total else maxOf(maxLines - 1, 1)
    val numberBase = GlanceModifier.size(18.dp)
    val numberModifier = if (cell.isToday) numberBase.background(GlanceTheme.colors.primary).cornerRadius(9.dp) else numberBase
    val numberColor = when {
        cell.isToday -> GlanceTheme.colors.onPrimary
        cell.inMonth -> GlanceTheme.colors.onBackground
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().background(lineColor).padding(end = 1.dp, bottom = 1.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).padding(top = 2.dp).clickable(onClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = numberModifier, contentAlignment = Alignment.Center) {
                Text(
                    text = cell.label,
                    style = TextStyle(color = numberColor, fontSize = 11.sp, fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal)
                )
            }
            cell.items.take(shown).forEach { item -> MonthItemChip(item) }
            if (total > shown) {
                Text(
                    text = "+" + (total - shown),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp)
                )
            }
        }
    }
}

class MonthWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = widgetEntryPoint(context)
        val p0 = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val initial = loadMonth(
            context,
            p0[WidgetPrefs.MONTH_OFFSET] ?: 0,
            p0[WidgetPrefs.MONTH_MARK_EVENTS] ?: true,
            p0[WidgetPrefs.MONTH_MARK_TASKS] ?: true
        )

        provideContent {
            val prefs = currentState<Preferences>()
            val offset = prefs[WidgetPrefs.MONTH_OFFSET] ?: 0
            val tick = prefs[WidgetPrefs.REFRESH_TICK] ?: 0
            val showEvents = prefs[WidgetPrefs.MONTH_MARK_EVENTS] ?: true
            val showTasks = prefs[WidgetPrefs.MONTH_MARK_TASKS] ?: true
            val tasksTrigger by entry.taskDao().getAllActiveTasks().collectAsState(initial = emptyList())
            val eventsTrigger by entry.eventDao().getActiveEventsFrom(0L).collectAsState(initial = emptyList())
            var data by remember { mutableStateOf(initial) }
            LaunchedEffect(offset, tick, showEvents, showTasks, tasksTrigger, eventsTrigger) {
                data = loadMonth(context, offset, showEvents, showTasks)
            }
            val size = LocalSize.current
            val maxLines = (((size.height.value - 64f) / 6f - 20f) / 12f).toInt().coerceIn(1, 4)
            val lineColor = ColorProvider(Color(0x66888888))

            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp)) {
                    WidgetHeader(
                        title = data.title,
                        buttons = listOf(
                            HeaderButton(R.drawable.ic_widget_today, actionRunCallback<ShiftMonthCallback>(actionParametersOf(WidgetKeys.Delta to 0))),
                            HeaderButton(R.drawable.ic_widget_prev, actionRunCallback<ShiftMonthCallback>(actionParametersOf(WidgetKeys.Delta to -1))),
                            HeaderButton(R.drawable.ic_widget_next, actionRunCallback<ShiftMonthCallback>(actionParametersOf(WidgetKeys.Delta to 1))),
                            HeaderButton(R.drawable.ic_widget_refresh, actionRunCallback<RefreshWidgetCallback>()),
                            HeaderButton(R.drawable.ic_widget_add, actionStartActivity(widgetDayIntent(context, data.todayEpoch, "monthadd")))
                        )
                    )
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        data.headers.forEach { header ->
                            Text(
                                text = header,
                                style = TextStyle(
                                    color = GlanceTheme.colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }
                    data.weeks.forEach { week ->
                        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                            week.forEach { cell ->
                                MonthDayCell(
                                    cell = cell,
                                    maxLines = maxLines,
                                    lineColor = lineColor,
                                    onClick = actionStartActivity(widgetDayIntent(context, cell.epoch, "month"))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}