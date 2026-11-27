package com.example.taskflow.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.projectTodayItems
import com.example.taskflow.ui.projects.projectStatusMessage

// Clave de preferencias del widget: id del proyecto elegido
val PROJECT_WIDGET_ID_KEY = longPreferencesKey("project_id")

data class ProjectWidgetData(
    val projectId: Long?,
    val name: String,
    val progress: Int,
    val statusLine: String,
    val todayLine: String?,
    val found: Boolean
)

private suspend fun loadProjectWidget(context: Context, projectId: Long?): ProjectWidgetData {
    val empty = ProjectWidgetData(null, "", 0, "", null, false)
    if (projectId == null) return empty
    val entry = widgetEntryPoint(context)
    val repo = entry.projectRepository()
    val today = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
    val project = repo.getProject(projectId) ?: return empty
    if (project.deletedAt != null) return empty
    val plan = repo.loadPlan(projectId, today) ?: return empty
    val todayTitle = entry.taskDao().getProjectTasksSync(projectId)
        .filter { it.date == today && !it.isCompleted }
        .minByOrNull { it.id }
        ?.title
    val statusLine = projectStatusMessage(
        context,
        project.status,
        plan.status,
        plan.marginDays,
        plan.overdueDays
    )
    return ProjectWidgetData(
        projectId = projectId,
        name = project.name,
        progress = plan.progressPercent,
        statusLine = statusLine,
        todayLine = todayTitle,
        found = true
    )
}

class ProjectWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val data = loadProjectWidget(context, prefs[PROJECT_WIDGET_ID_KEY])
        val emptyText = context.getString(R.string.p2e_widget_choose)
        val openIntent = widgetOpenIntent(
            context,
            if (data.projectId != null) "project_detail/" + data.projectId else "projects"
        )

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background).cornerRadius(20.dp)
                        .padding(10.dp)
                        .clickable(actionStartActivity(openIntent)),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (!data.found) {
                        Text(
                            text = emptyText,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
                        )
                    } else {
                        Text(
                            text = data.name,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        )
                        Text(
                            text = context.getString(R.string.p2e_widget_progress, data.progress),
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                        )
                        Text(
                            text = data.statusLine,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                        )
                        val today = data.todayLine
                        if (today != null) {
                            Text(
                                text = today,
                                maxLines = 2,
                                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

class ProjectWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProjectWidget()
}
