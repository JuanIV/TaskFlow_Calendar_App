package com.example.taskflow.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.taskflow.R
import com.example.taskflow.data.daos.FocusLogDao
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.TodayStateEvaluator
import com.example.taskflow.domain.currentStreak
import com.example.taskflow.domain.dayRecordsToStatuses
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.mainactivity
import com.example.taskflow.scheduling.AlarmContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklySummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val recordDao: RecordDao,
    private val focusLogDao: FocusLogDao,
    private val todayState: TodayStateEvaluator,
    private val settingsRepo: SettingsRepository
) {
    private fun durationString(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> context.getString(R.string.s3_duration_min, m)
            m == 0 -> context.getString(R.string.s3_duration_h, h)
            else -> context.getString(R.string.s3_duration_hm, h, m)
        }
    }

    suspend fun show() {
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        val from = today - 6
        val fromMs = LocalDate.ofEpochDay(from).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()

        val completed = taskDao.getAllActiveTasksSync()
            .count { it.parentTaskId == null && it.isCompleted && (it.completedAt ?: 0L) in fromMs..nowMs }
        val allRecords = recordDao.getAllDayRecordsSync()
        val week = allRecords.filter {
            it.date in from..today && (it.status == "COMPLETED" || it.status == "FAILED" || it.status == "GRACE")
        }
        val focus = focusLogDao.focusMinutesBetween(fromMs, nowMs)
        val streak = currentStreak(dayRecordsToStatuses(allRecords), todayState.globalState(today))

        val lines = mutableListOf<String>()
        lines.add(context.getString(R.string.s3_weekly_tasks, completed))
        if (week.isNotEmpty()) {
            lines.add(context.getString(R.string.s3_weekly_days, week.count { it.status == "COMPLETED" }, week.size))
        }
        if (focus > 0) lines.add(context.getString(R.string.s3_weekly_focus, durationString(focus)))
        lines.add(context.getString(R.string.s3_weekly_streak, streak))

        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val intent = Intent(context, mainactivity::class.java).apply {
            putExtra(AlarmContract.EXTRA_OPEN_ROUTE, "statistics")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 5002, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationChannels.SUMMARIES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.s3_weekly_title))
            .setContentText(lines.first())
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(103, notification)
    }
}
