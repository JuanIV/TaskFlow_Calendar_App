package com.example.taskflow.widgets

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
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
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.routineAppliesOn
import com.example.taskflow.domain.taskAppliesOn

private data class RoutineWidgetRow(val id: Long, val name: String, val colorArgb: Int, val done: Int, val required: Int)

private suspend fun loadRoutines(context: Context, onlyPending: Boolean): List<RoutineWidgetRow> {
    val entry = widgetEntryPoint(context)
    val today = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val completed = entry.routineDao().getAllCompletionsOnDateSync(today).map { it.routineTaskId }.toSet()
    val rows = ArrayList<RoutineWidgetRow>()
    for (routine in entry.routineDao().getActiveRoutinesSync()) {
        if (!routineAppliesOn(routine, today)) continue
        val tasks = entry.routineDao().getTasksForRoutineSync(routine.id).filter { taskAppliesOn(it, today) }
        val mandatory = tasks.filter { !it.isOptional }
        val required = if (mandatory.isEmpty()) tasks else mandatory
        if (required.isEmpty()) continue
        val done = required.count { it.id in completed }
        if (onlyPending && done >= required.size) continue
        rows.add(RoutineWidgetRow(routine.id, routine.name, routine.colorArgb, done, required.size))
    }
    return rows.sortedBy { it.done >= it.required }
}

class RoutinesWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val rows = loadRoutines(context, prefs[WidgetPrefs.ROUTINES_ONLY_PENDING] ?: false)
        val title = context.getString(R.string.s3_widget_routines_label)
        val emptyText = context.getString(R.string.s3_widget_routines_none)
        val listIntent = widgetOpenIntent(context, "routines")
        val intents = rows.associate { it.id to widgetOpenIntent(context, "routine_detail/" + it.id) }

        provideContent {
            GlanceTheme {
                Column(modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp).padding(8.dp)) {
                    Text(
                        text = title,
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(listIntent)).padding(bottom = 4.dp)
                    )
                    if (rows.isEmpty()) {
                        Text(text = emptyText, style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp))
                    } else {
                        LazyColumn {
                            items(rows) { row ->
                                val finished = row.done >= row.required
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(
                                        actionStartActivity(intents.getValue(row.id))
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = GlanceModifier.width(4.dp).height(20.dp).background(ColorProvider(Color(row.colorArgb)))) { }
                                    Spacer(modifier = GlanceModifier.width(6.dp))
                                    Text(
                                        text = row.name,
                                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp),
                                        modifier = GlanceModifier.defaultWeight()
                                    )
                                    Text(
                                        text = context.getString(R.string.s3_widget_progress, row.done, row.required),
                                        style = TextStyle(
                                            color = if (finished) GlanceTheme.colors.primary else GlanceTheme.colors.onBackground,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
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
}

class RoutinesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RoutinesWidget()
}