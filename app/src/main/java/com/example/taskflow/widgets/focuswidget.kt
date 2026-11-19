package com.example.taskflow.widgets

import android.content.Context
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.taskflow.R
import com.example.taskflow.domain.formatMinute
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun focusDuration(context: Context, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> context.getString(R.string.s3_duration_min, m)
        m == 0 -> context.getString(R.string.s3_duration_h, h)
        else -> context.getString(R.string.s3_duration_hm, h, m)
    }
}

private fun focusPhaseLabel(context: Context, phase: String): String = when (phase) {
    "SHORT_BREAK" -> context.getString(R.string.s3_focus_phase_short)
    "LONG_BREAK" -> context.getString(R.string.s3_focus_phase_long)
    "TIMER" -> context.getString(R.string.s3_focus_phase_timer)
    else -> context.getString(R.string.s3_focus_phase_focus)
}

class FocusWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val showTotal = prefs[WidgetPrefs.FOCUS_SHOW_TOTAL] ?: false
        val entry = widgetEntryPoint(context)

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayMinutes = entry.focusLogDao().focusMinutesBetween(startOfDay, startOfDay + 24L * 60 * 60 * 1000)
        val totalMinutes = entry.focusLogDao().totalFocusMinutes()
        val state = entry.settingsRepo().focusStateFlow.first()
        val running = state.active && state.endMs > System.currentTimeMillis()

        val title = context.getString(R.string.s3_widget_focus_label)
        val stateLine: String = if (running) {
            val end = Instant.ofEpochMilli(state.endMs).atZone(zone)
            focusPhaseLabel(context, state.phase) + " - " +
                context.getString(R.string.s3_widget_focus_until, formatMinute(end.hour * 60 + end.minute))
        } else {
            context.getString(R.string.s3_widget_focus_idle)
        }
        val todayText = context.getString(R.string.s3_widget_focus_today, focusDuration(context, todayMinutes))
        val totalText = context.getString(R.string.s3_widget_focus_total, focusDuration(context, totalMinutes))
        val openIntent = widgetOpenIntent(context, "focus")

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(8.dp)
                        .clickable(actionStartActivity(openIntent)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                    Text(
                        text = todayText,
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    )
                    if (showTotal) {
                        Text(
                            text = totalText,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                        )
                    }
                    Text(
                        text = stateLine,
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}
