package com.example.taskflow.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.example.taskflow.R

object NotificationChannels {
    const val EVENT_REMINDERS = "event_reminders"
    const val TASK_REMINDERS = "task_reminders"
    const val ROUTINE_ALERTS = "routine_alerts"
    const val SUMMARIES = "summaries"
    const val PERSISTENT_TASKS = "persistent_tasks"
    const val PERSISTENT_ROUTINES = "persistent_routines"
    const val LOOSE_TASKS = "loose_tasks"
    const val FOCUS_RUNNING = "focus_running"
    const val FOCUS_ALERTS = "focus_alerts"
    const val ALARMS = "event_alarms"
    const val ACHIEVEMENTS = "achievements"
    const val ALARM_SCREEN = "alarm_screen"
    const val PROJECT_TODAY = "project_today"
    const val PROJECT_RISK = "project_risk"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channels = listOf(
            NotificationChannel(EVENT_REMINDERS, context.getString(R.string.s2_ch_event_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.s2_ch_event_desc)
            },
            NotificationChannel(TASK_REMINDERS, context.getString(R.string.s2_ch_task_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.s2_ch_task_desc)
            },
            NotificationChannel(ROUTINE_ALERTS, context.getString(R.string.s2_ch_alert_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.s2_ch_alert_desc)
            },
            NotificationChannel(SUMMARIES, context.getString(R.string.s2_ch_summary_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.s2_ch_summary_desc)
            },
            NotificationChannel(PERSISTENT_TASKS, context.getString(R.string.s2_ch_tasks_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.s2_ch_tasks_desc)
                setShowBadge(false)
            },
            NotificationChannel(PERSISTENT_ROUTINES, context.getString(R.string.s2_ch_routines_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.s2_ch_routines_desc)
                setShowBadge(false)
            },
            NotificationChannel(LOOSE_TASKS, context.getString(R.string.s2_ch_loose_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.s2_ch_loose_desc)
            },
            NotificationChannel(FOCUS_RUNNING, context.getString(R.string.s3_ch_focus_running_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.s3_ch_focus_running_desc)
                setShowBadge(false)
            },
            NotificationChannel(FOCUS_ALERTS, context.getString(R.string.s3_ch_focus_alerts_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.s3_ch_focus_alerts_desc)
            },
            NotificationChannel(ALARMS, context.getString(R.string.s3_ch_alarms_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.s3_ch_alarms_desc)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAttributes)
                enableVibration(true)
            },
            NotificationChannel(ALARM_SCREEN, context.getString(R.string.s3_ch_alarm_screen_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.s3_ch_alarm_screen_desc)
                setSound(null, null)
                enableVibration(true)
            },
            NotificationChannel(PROJECT_TODAY, context.getString(R.string.p1d_ch_today_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.p1d_ch_today_desc)
                setShowBadge(false)
            },
            NotificationChannel(PROJECT_RISK, context.getString(R.string.p1d_ch_risk_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.p1d_ch_risk_desc)
            },
            NotificationChannel(ACHIEVEMENTS, context.getString(R.string.s3_ch_achievements_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.s3_ch_achievements_desc)
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }
}
