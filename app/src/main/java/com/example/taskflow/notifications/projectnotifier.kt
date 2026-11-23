package com.example.taskflow.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.taskflow.R
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.ProjectTodayItem
import com.example.taskflow.domain.RiskAlert
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.projectTodayItems
import com.example.taskflow.domain.riskAlertFor
import com.example.taskflow.mainactivity
import com.example.taskflow.scheduling.AlarmContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val settingsRepo: SettingsRepository
) {
    private companion object {
        const val TODAY_ID = 6_000_001
        const val RISK_BASE = 6_100_000
        const val MAX_ROWS = 5
    }

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun openProjects(requestCode: Int, route: String): PendingIntent {
        val intent = Intent(context, mainactivity::class.java).apply {
            putExtra(AlarmContract.EXTRA_OPEN_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Notificacion fija con las partes de hoy de todos los proyectos activos
    suspend fun refreshToday() {
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        val items = ArrayList<ProjectTodayItem>()
        for ((project, plan) in repository.getActiveProjectPlansSync(today)) {
            val titles = repository.getNodeTitles(project.id)
            items.addAll(projectTodayItems(project.id, project.name, plan, titles, today))
        }
        if (items.isEmpty()) {
            manager.cancel(TODAY_ID)
            return
        }
        val lines = items.take(MAX_ROWS).map { it.label + " (" + com.example.taskflow.domain.formatHours(it.minutes) + " h)" }
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        if (items.size > MAX_ROWS) {
            style.addLine(context.getString(R.string.p1d_and_more, items.size - MAX_ROWS))
        }
        val builder = NotificationCompat.Builder(context, NotificationChannels.PROJECT_TODAY)
            .setSmallIcon(R.drawable.ic_stat_taskflow)
            .setContentTitle(context.getString(R.string.p1d_today_title))
            .setContentText(lines.first())
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openProjects(TODAY_ID, "projects"))
        manager.notify(TODAY_ID, builder.build())
    }

    // Compara el estado de cada proyecto con el ultimo notificado y avisa si empeora
    suspend fun checkRisks() {
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        for ((project, plan) in repository.getActiveProjectPlansSync(today)) {
            val previous = settingsRepo.getProjectRiskSync(project.id)
            val current = plan.status
            val alert = riskAlertFor(previous, current)
            if (current != previous) settingsRepo.setProjectRisk(project.id, current)
            if (alert == RiskAlert.NONE) continue
            val text = if (alert == RiskAlert.AT_RISK) context.getString(R.string.p1d_risk_at_risk) else context.getString(R.string.p1d_risk_no_margin)
            val id = RISK_BASE + project.id.toInt()
            val builder = NotificationCompat.Builder(context, NotificationChannels.PROJECT_RISK)
                .setSmallIcon(R.drawable.ic_stat_taskflow)
                .setContentTitle(project.name)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openProjects(id, "project_detail/" + project.id))
            manager.notify(id, builder.build())
        }
    }
}
