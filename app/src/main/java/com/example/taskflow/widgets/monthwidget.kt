package com.example.taskflow.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.taskflow.domain.expandEvents
import com.example.taskflow.domain.isVisibleIn
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.ui.calendar.getDaysInMonthGrid
import com.example.taskflow.ui.calendar.groupByDay
import com.example.taskflow.ui.calendar.weekdayHeaderRes
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class MonthCell(val epoch: Long, val label: String, val inMonth: Boolean, val isToday: Boolean, val marked: Boolean)
private data class MonthData(val title: String, val headers: List<String>, val weeks: List<List<MonthCell>>)

private suspend fun loadMonth(context: Context, markEvents: Boolean, markTasks: Boolean): MonthData {
    val entry = widgetEntryPoint(context)
    val todayEpoch = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val todayDate = LocalDate.ofEpochDay(todayEpoch)
    val month = YearMonth.from(todayDate)
    val first = DayOfWeek.of(entry.settingsRepo().firstDayFlow.first().coerceIn(1, 7))
    val days = getDaysInMonthGrid(month, first)
    val from = days.first().toEpochDay()
    val to = days.last().toEpochDay()

    val eventDays: Set<Long> = if (markEvents) {
        val events = entry.eventDao().getActiveEventsFromSync(from - 365)
        val exceptions = if (events.isEmpty()) emptyList() else entry.exceptionDao().getExceptionsForEvents(events.map { it.id })
        groupByDay(expandEvents(events, exceptions, from, to).filter { it.isVisibleIn("MONTH") }).keys
    } else {
        emptySet()
    }
    val taskDays: Set<Long> = if (markTasks) {
        entry.taskDao().getAllActiveTasksSync()
            .filter { it.parentTaskId == null && it.date != null && !it.isCompleted }
            .map { it.date ?: 0L }
            .toSet()
    } else {
        emptySet()
    }

    val weeks = (0 until 6).map { w ->
        (0 until 7).map { c ->
            val d = days[w * 7 + c]
            val epoch = d.toEpochDay()
            MonthCell(epoch, d.dayOfMonth.toString(), d.month == month.month, epoch == todayEpoch, epoch in eventDays || epoch in taskDays)
        }
    }
    val title = todayDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))).replaceFirstChar { it.uppercase() }
    return MonthData(title, weekdayHeaderRes(first).map { context.getString(it) }, weeks)
}

class MonthWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val data = loadMonth(
            context,
            prefs[WidgetPrefs.MONTH_MARK_EVENTS] ?: true,
            prefs[WidgetPrefs.MONTH_MARK_TASKS] ?: true
        )
        val calendarIntent = widgetOpenIntent(context, "calendar")
        val dayIntents: Map<Long, Intent> = data.weeks.flatten().associate { cell ->
            cell.epoch to widgetOpenIntent(context, "day/" + cell.epoch).apply {
                this.data = Uri.parse("taskflow://widget/day/" + cell.epoch)
            }
        }

        provideContent {
            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).padding(6.dp)) {
                    Text(
                        text = data.title,
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp).clickable(actionStartActivity(calendarIntent))
                    )
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
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
                                val base = GlanceModifier.defaultWeight().fillMaxSize()
                                val withBg = if (cell.isToday) base.background(GlanceTheme.colors.primaryContainer) else base
                                Column(
                                    modifier = withBg.clickable(actionStartActivity(dayIntents.getValue(cell.epoch))),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cell.label,
                                        style = TextStyle(
                                            color = when {
                                                cell.isToday -> GlanceTheme.colors.onPrimaryContainer
                                                cell.inMonth -> GlanceTheme.colors.onBackground
                                                else -> GlanceTheme.colors.onSurfaceVariant
                                            },
                                            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                    Text(
                                        text = if (cell.marked) "\u2022" else " ",
                                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 9.sp, textAlign = TextAlign.Center)
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

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
