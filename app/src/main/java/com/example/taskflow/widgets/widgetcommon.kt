package com.example.taskflow.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.domain.expandEvents
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.domain.isVisibleIn
import com.example.taskflow.ui.calendar.groupByDay

data class HeaderButton(val iconRes: Int, val action: Action)

// Cabecera comun de todos los widgets: titulo a la izquierda y botones a la derecha
@Composable
fun WidgetHeader(title: String, buttons: List<HeaderButton>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.primary).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp),
            modifier = GlanceModifier.defaultWeight()
        )
        buttons.forEach { b ->
            Image(
                provider = ImageProvider(b.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(32.dp).padding(5.dp).clickable(b.action)
            )
        }
    }
}

// Elemento (evento o tarea) de un dia
data class DayItem(
    val text: String,
    val colorArgb: Int,
    val isTask: Boolean,
    val completed: Boolean,
    val taskId: Long,
    val route: String,
    val timeText: String
)

// Eventos (primero) y tareas (despues) agrupados por dia. viewType = null no filtra eventos secundarios.
suspend fun loadDayItems(
    context: Context,
    from: Long,
    to: Long,
    showEvents: Boolean,
    showTasks: Boolean,
    viewType: String?
): Map<Long, List<DayItem>> {
    val entry = widgetEntryPoint(context)
    val allDayText = context.getString(R.string.s2_all_day)
    val result = HashMap<Long, MutableList<DayItem>>()

    if (showEvents) {
        val events = entry.eventDao().getActiveEventsFromSync(from - 365)
        if (events.isNotEmpty()) {
            val exceptions = entry.exceptionDao().getExceptionsForEvents(events.map { it.id })
            val occurrences = expandEvents(events, exceptions, from, to).filter { viewType == null || it.isVisibleIn(viewType) }
            for ((day, list) in groupByDay(occurrences)) {
                val target = result.getOrPut(day) { mutableListOf() }
                for (e in list) {
                    val start = e.startMinute
                    val end = e.endMinute
                    val time = if (e.allDay || start == null) allDayText
                    else formatMinute(start) + (if (end != null) " - " + formatMinute(end) else "")
                    target.add(DayItem(e.title, e.colorArgb, false, false, 0L, "event_detail/" + e.eventId + "/" + e.originalDate, time))
                }
            }
        }
    }

    if (showTasks) {
        val tasks = entry.taskDao().getAllActiveTasksSync()
            .filter { val d = it.date; it.parentTaskId == null && d != null && d >= from && d <= to }
            .sortedWith(compareBy<TaskEntity>({ it.isCompleted }, { -it.priority }, { it.sortOrder }))
        for (t in tasks) {
            val d = t.date ?: continue
            val m = t.dueMinuteOfDay
            result.getOrPut(d) { mutableListOf() }
                .add(DayItem(t.title, 0, true, t.isCompleted, t.id, "", if (m != null) formatMinute(m) else allDayText))
        }
    }
    return result
}