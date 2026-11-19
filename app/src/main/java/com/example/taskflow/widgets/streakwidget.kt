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
import com.example.taskflow.domain.bestStreak
import com.example.taskflow.domain.currentStreak
import com.example.taskflow.domain.dayRecordsToStatuses
import com.example.taskflow.domain.logicalDateNow

private data class StreakData(val current: Int, val best: Int)

private suspend fun loadStreak(context: Context): StreakData {
    val entry = widgetEntryPoint(context)
    val today = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val statuses = dayRecordsToStatuses(entry.recordDao().getAllDayRecordsSync())
    val live = entry.todayState().globalState(today)
    return StreakData(currentStreak(statuses, live), bestStreak(statuses, live))
}

class StreakWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val showBest = prefs[WidgetPrefs.STREAK_SHOW_BEST] ?: true
        val data = loadStreak(context)
        val title = context.getString(R.string.s3_widget_streak_title)
        val daysText = context.getString(R.string.s3_widget_streak_days, data.current)
        val bestText = context.getString(R.string.s3_widget_streak_best, data.best)
        val openIntent = widgetOpenIntent(context, "statistics")

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
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                    )
                    Text(
                        text = data.current.toString(),
                        style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 40.sp)
                    )
                    Text(
                        text = daysText,
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                    )
                    if (showBest) {
                        Text(
                            text = bestText,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
