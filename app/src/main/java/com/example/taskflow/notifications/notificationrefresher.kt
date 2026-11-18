package com.example.taskflow.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.taskflow.R
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.EventExceptionDao
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.TodayStateEvaluator
import com.example.taskflow.domain.currentStreak
import com.example.taskflow.domain.dayRecordsToStatuses
import com.example.taskflow.domain.expandEvents
import com.example.taskflow.domain.firstPendingInSequence
import com.example.taskflow.domain.formatMinute
import com.example.taskflow.domain.getLogicalMinuteOffset
import com.example.taskflow.domain.orderForSequence
import com.example.taskflow.domain.getRoutineTasksForNotification
import com.example.taskflow.domain.getTodayTasksForNotification
import com.example.taskflow.domain.isQuietNow
import com.example.taskflow.domain.isRoutineNotificationVisible
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.routineAppliesOn
import com.example.taskflow.domain.taskAppliesOn
import com.example.taskflow.mainactivity
import com.example.taskflow.alarm.AlarmActivity
import com.example.taskflow.scheduling.AlarmContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val routineDao: RoutineDao,
    private val eventDao: EventDao,
    private val exceptionDao: EventExceptionDao,
    private val recordDao: RecordDao,
    private val todayState: TodayStateEvaluator,
    private val settingsRepo: SettingsRepository
) {
    private companion object {
        const val MAX_ROWS = 5
    }

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun openIntent(requestCode: Int, route: String): PendingIntent {
        val intent = Intent(context, mainactivity::class.java).apply {
            putExtra(AlarmContract.EXTRA_OPEN_ROUTE, route)
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags())
    }

    private fun receiverIntent(requestCode: Int, action: String, vararg extras: Pair<String, Long>): PendingIntent {
        val intent = Intent(context, TaskActionReceiver::class.java).apply {
            this.action = action
            extras.forEach { putExtra(it.first, it.second) }
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags())
    }

    private fun formatTime(minute: Int): String = formatMinute(minute)

    private suspend fun isQuietNowSync(dayStart: Int): Boolean {
        if (!settingsRepo.quietEnabledFlow.first()) return false
        val now = ZonedDateTime.now()
        return isQuietNow(
            now.hour * 60 + now.minute,
            settingsRepo.quietStartFlow.first(),
            settingsRepo.quietEndFlow.first(),
            dayStart
        )
    }

    // ---------------------------------------------------------------
    // Notificaciones persistentes
    // ---------------------------------------------------------------

    suspend fun refreshAll() {
        val dayStart = settingsRepo.getDayStartHourSync()
        val now = ZonedDateTime.now()
        val currentMinute = now.hour * 60 + now.minute
        val todayEpoch = logicalDateNow(dayStart)
        refreshTodayTasks(todayEpoch, currentMinute, dayStart)
        refreshRoutines(todayEpoch, currentMinute, dayStart)
    }

    private suspend fun refreshTodayTasks(todayEpoch: Long, currentMinute: Int, dayStart: Int) {
        val offNow = getLogicalMinuteOffset(currentMinute, dayStart)
        val offTasks = getLogicalMinuteOffset(settingsRepo.todayTasksMinuteFlow.first(), dayStart)
        val overdueMode = settingsRepo.overdueModeFlow.first()
        val pending = getTodayTasksForNotification(taskDao.getTasksUpToDateSync(todayEpoch), todayEpoch, overdueMode)

        val undoIds = settingsRepo.getUndoBatchSync().first
        val expires = settingsRepo.getUndoExpiresSync()
        val nowMs = System.currentTimeMillis()
        val undoActive = undoIds.isNotEmpty() && expires > nowMs
        if (undoIds.isNotEmpty() && !undoActive) settingsRepo.setUndoBatch("", "")

        if (pending.isNotEmpty()) {
            if (undoIds.isNotEmpty()) settingsRepo.setUndoBatch("", "")
            if (offNow >= offTasks) showTodayTasks(pending, todayEpoch) else manager.cancel(NotificationIds.TODAY_TASKS)
        } else if (undoActive) {
            showAllDone(expires - nowMs)
        } else {
            manager.cancel(NotificationIds.TODAY_TASKS)
        }
    }

    private suspend fun showTodayTasks(pending: List<TaskEntity>, todayEpoch: Long) {
        val title = context.resources.getQuantityString(R.plurals.s2_tasks_pending, pending.size, pending.size)
        val rows = pending.take(MAX_ROWS).map { task ->
            val subs = taskDao.getSubtasksForTaskSync(task.id)
            val sb = StringBuilder()
            val taskDate = task.date
            if (taskDate != null && taskDate < todayEpoch) sb.append(context.getString(R.string.s2_overdue_prefix))
            sb.append(task.title)
            if (subs.isNotEmpty()) sb.append(" (").append(subs.count { it.isCompleted }).append("/").append(subs.size).append(")")
            Pair(task, sb.toString())
        }

        val collapsed = RemoteViews(context.packageName, R.layout.notification_collapsed).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_subtitle, rows.first().second)
        }
        val expanded = RemoteViews(context.packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.notification_title, title)
            removeAllViews(R.id.tasks_container)
            for ((task, text) in rows) {
                val row = RemoteViews(context.packageName, R.layout.notification_task_row)
                row.setTextViewText(R.id.task_title, text)
                row.setImageViewResource(R.id.checkbox_icon, R.drawable.ic_checkbox_unchecked)
                val toggle = receiverIntent(task.id.toInt(), NotificationActions.TOGGLE_TASK, NotificationActions.EXTRA_TASK_ID to task.id)
                row.setOnClickPendingIntent(R.id.checkbox_icon, toggle)
                row.setOnClickPendingIntent(R.id.row_container, toggle)
                addView(R.id.tasks_container, row)
            }
            if (pending.size > MAX_ROWS) {
                setViewVisibility(R.id.footer_text, View.VISIBLE)
                setTextViewText(R.id.footer_text, context.getString(R.string.s2_and_n_more, pending.size - MAX_ROWS))
            } else {
                setViewVisibility(R.id.footer_text, View.GONE)
            }
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.PERSISTENT_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openIntent(NotificationIds.TODAY_TASKS, "tasks"))
            .setDeleteIntent(receiverIntent(12, NotificationActions.DISMISS_TASKS))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.s2_complete_all), receiverIntent(10, NotificationActions.COMPLETE_ALL_TASKS))
            .addAction(0, context.getString(R.string.s2_postpone_tomorrow), receiverIntent(11, NotificationActions.POSTPONE_TODAY))
        manager.notify(NotificationIds.TODAY_TASKS, builder.build())
    }

    private fun showAllDone(remainingMs: Long) {
        val builder = NotificationCompat.Builder(context, NotificationChannels.PERSISTENT_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.s2_all_done_title))
            .setContentText(context.getString(R.string.s2_all_done_text))
            .setContentIntent(openIntent(NotificationIds.TODAY_TASKS, "tasks"))
            .addAction(0, context.getString(R.string.s2_undo), receiverIntent(13, NotificationActions.UNDO_TASKS))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(remainingMs.coerceAtLeast(1000L))
        manager.notify(NotificationIds.TODAY_TASKS, builder.build())
    }

    private suspend fun refreshRoutines(todayEpoch: Long, currentMinute: Int, dayStart: Int) {
        val routines = routineDao.getActiveRoutinesSync()
        val completions = routineDao.getAllCompletionsOnDateSync(todayEpoch)
        val visibleIds = mutableSetOf<Int>()

        for (routine in routines) {
            val tasks = routineDao.getTasksForRoutineSync(routine.id)
            if (!isRoutineNotificationVisible(routine, todayEpoch, currentMinute, dayStart, tasks, completions)) continue
            val id = NotificationIds.routine(routine.id)
            visibleIds.add(id)
            showRoutine(routine, getRoutineTasksForNotification(tasks, completions, todayEpoch), id)
        }

        val rangeEnd = NotificationIds.ROUTINE_BASE + NotificationIds.ROUTINE_RANGE
        for (sbn in manager.activeNotifications) {
            val id = sbn.id
            if (id in NotificationIds.ROUTINE_BASE..rangeEnd && id !in visibleIds) manager.cancel(id)
            if (id in 2000..2999) manager.cancel(id)
        }
    }

    private fun showRoutine(routine: RoutineEntity, items: List<Pair<RoutineTaskEntity, Boolean>>, notificationId: Int) {
        val done = items.count { it.second }
        val progress = context.getString(R.string.s2_routine_progress, done, items.size)
        val ordered = items.sortedBy { it.second }
        val doneIds = items.filter { it.second }.map { it.first.id }.toSet()
        val firstPendingId = firstPendingInSequence(orderForSequence(items.map { it.first }), doneIds)

        val collapsed = RemoteViews(context.packageName, R.layout.notification_collapsed).apply {
            setTextViewText(R.id.notification_title, routine.name)
            setTextViewText(R.id.notification_subtitle, progress)
        }
        val expanded = RemoteViews(context.packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.notification_title, routine.name + " - " + progress)
            removeAllViews(R.id.tasks_container)
            for ((task, isDone) in ordered.take(MAX_ROWS)) {
                val row = RemoteViews(context.packageName, R.layout.notification_task_row)
                val locked = routine.sequentialMode && !isDone && task.id != firstPendingId
                val baseText = if (task.isOptional) task.title + context.getString(R.string.s2_optional_suffix) else task.title
                val text = if (locked) baseText + context.getString(R.string.s3_seq_locked_suffix) else baseText
                row.setTextViewText(R.id.task_title, text)
                row.setImageViewResource(R.id.checkbox_icon, if (isDone) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked)
                val toggle = receiverIntent(
                    task.id.toInt() + 500, NotificationActions.TOGGLE_ROUTINE_TASK,
                    NotificationActions.EXTRA_ROUTINE_TASK_ID to task.id
                )
                if (!locked) row.setOnClickPendingIntent(R.id.checkbox_icon, toggle)
                if (!locked) row.setOnClickPendingIntent(R.id.row_container, toggle)
                addView(R.id.tasks_container, row)
            }
            if (ordered.size > MAX_ROWS) {
                setViewVisibility(R.id.footer_text, View.VISIBLE)
                setTextViewText(R.id.footer_text, context.getString(R.string.s2_and_n_more, ordered.size - MAX_ROWS))
            } else {
                setViewVisibility(R.id.footer_text, View.GONE)
            }
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.PERSISTENT_ROUTINES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setColor(routine.colorArgb)
            .setContentIntent(openIntent(notificationId, "routine_detail/${routine.id}"))
            .setDeleteIntent(receiverIntent(routine.id.toInt() + 200, NotificationActions.DISMISS_ROUTINE))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0, context.getString(R.string.s2_complete_all),
                receiverIntent(routine.id.toInt() + 100, NotificationActions.COMPLETE_ALL_ROUTINE, NotificationActions.EXTRA_ROUTINE_ID to routine.id)
            )
        manager.notify(notificationId, builder.build())
    }

    // ---------------------------------------------------------------
    // Avisos puntuales
    // ---------------------------------------------------------------

    suspend fun showEventReminder(eventId: Long, occurrenceOriginalDate: Long) {
        val event = eventDao.getEventById(eventId) ?: return
        if (event.deletedAt != null) return
        val exceptions = exceptionDao.getExceptionsForEvents(listOf(eventId))
        val occ = expandEvents(listOf(event), exceptions, occurrenceOriginalDate - 400, occurrenceOriginalDate + 400)
            .firstOrNull { it.originalDate == occurrenceOriginalDate } ?: return

        val startMin = occ.startMinute
        val endMin = occ.endMinute
        val timeText = if (occ.allDay || startMin == null) {
            context.getString(R.string.s2_all_day)
        } else {
            formatTime(startMin) + (if (endMin != null) " - " + formatTime(endMin) else "")
        }
        val id = NotificationIds.event(eventId, occurrenceOriginalDate)
        if (event.alarmMode) {
            postAlarm(eventId, occurrenceOriginalDate, occ.title, timeText, id)
            return
        }
        val builder = NotificationCompat.Builder(context, NotificationChannels.EVENT_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(occ.title)
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(id, "event_detail/$eventId/$occurrenceOriginalDate"))
            .setAutoCancel(true)
        manager.notify(id, builder.build())
    }

    private fun postAlarm(eventId: Long, occDate: Long, title: String, timeText: String, notificationId: Int) {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_EVENT_ID, eventId)
            putExtra(AlarmActivity.EXTRA_OCC_DATE, occDate)
            putExtra(AlarmActivity.EXTRA_TITLE, title)
            putExtra(AlarmActivity.EXTRA_TIME, timeText)
            putExtra(AlarmActivity.EXTRA_NOTIFICATION_ID, notificationId)
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, notificationId, intent, flags())
        val builder = NotificationCompat.Builder(context, NotificationChannels.ALARM_SCREEN)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
        manager.notify(notificationId, builder.build())
    }

    suspend fun showTaskReminder(taskId: Long) {
        val task = taskDao.getTaskById(taskId) ?: return
        if (task.deletedAt != null || task.isCompleted) return
        val id = NotificationIds.taskReminder(taskId)
        val builder = NotificationCompat.Builder(context, NotificationChannels.TASK_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(task.title)
            .setContentText(context.getString(R.string.s2_task_reminder_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(id, "tasks"))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.s2_complete),
                receiverIntent(id, NotificationActions.COMPLETE_ONE_TASK, NotificationActions.EXTRA_TASK_ID to taskId))
        if (task.date != null) {
            builder.addAction(0, context.getString(R.string.s2_postpone_tomorrow),
                receiverIntent(id + 1, NotificationActions.POSTPONE_ONE_TASK, NotificationActions.EXTRA_TASK_ID to taskId))
        }
        manager.notify(id, builder.build())
    }

    suspend fun showRoutineEndWarning(routineId: Long) {
        val dayStart = settingsRepo.getDayStartHourSync()
        val today = logicalDateNow(dayStart)
        val routine = routineDao.getRoutineById(routineId) ?: return
        if (!routineAppliesOn(routine, today)) return
        val warn = routine.endWarningMinutes ?: return
        if (routine.endMinuteOfDay == null) return
        if (isQuietNowSync(dayStart)) return

        val tasks = routineDao.getTasksForRoutineSync(routineId).filter { taskAppliesOn(it, today) }
        val mandatory = tasks.filter { !it.isOptional }
        val required = if (mandatory.isEmpty()) tasks else mandatory
        val done = routineDao.getAllCompletionsOnDateSync(today).map { it.routineTaskId }.toSet()
        val pending = required.count { it.id !in done }
        if (pending == 0) return

        val builder = NotificationCompat.Builder(context, NotificationChannels.ROUTINE_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.s2_routine_end_title))
            .setContentText(context.getString(R.string.s2_routine_end_text, routine.name, warn, pending))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(NotificationIds.alert(routineId), "routine_detail/$routineId"))
            .setAutoCancel(true)
        manager.notify(NotificationIds.alert(routineId), builder.build())
    }

    suspend fun showLooseReminder() {
        if (!settingsRepo.looseEnabledFlow.first()) return
        val dayStart = settingsRepo.getDayStartHourSync()
        if (isQuietNowSync(dayStart)) return
        val loose = taskDao.getPendingLooseTasksSync()
        if (loose.isEmpty()) return

        val title = context.resources.getQuantityString(R.plurals.s2_loose_pending, loose.size, loose.size)
        val style = NotificationCompat.InboxStyle()
        loose.take(MAX_ROWS).forEach { style.addLine(it.title) }
        if (loose.size > MAX_ROWS) style.addLine(context.getString(R.string.s2_and_n_more, loose.size - MAX_ROWS))

        val builder = NotificationCompat.Builder(context, NotificationChannels.LOOSE_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(loose.first().title)
            .setStyle(style)
            .setContentIntent(openIntent(NotificationIds.LOOSE_TASKS, "tasks"))
            .setAutoCancel(true)
        manager.notify(NotificationIds.LOOSE_TASKS, builder.build())
    }

    // ---------------------------------------------------------------
    // Resumenes
    // ---------------------------------------------------------------

    private suspend fun occurrencesOn(epoch: Long): List<EventOccurrence> {
        val events = eventDao.getActiveEventsFromSync(epoch - 365)
        if (events.isEmpty()) return emptyList()
        val exceptions = exceptionDao.getExceptionsForEvents(events.map { it.id })
        return expandEvents(events, exceptions, epoch, epoch)
            .sortedWith(compareBy({ !it.allDay }, { it.startMinute ?: 0 }))
    }

    private fun eventLine(occ: EventOccurrence): String {
        val startMin = occ.startMinute
        return if (occ.allDay || startMin == null) occ.title else formatTime(startMin) + " " + occ.title
    }

    suspend fun showSummary(isMorning: Boolean) {
        val dayStart = settingsRepo.getDayStartHourSync()
        if (isQuietNowSync(dayStart)) return
        val today = logicalDateNow(dayStart)

        val events = occurrencesOn(today)
        val topTasks = taskDao.getTasksForDateSync(today).filter { it.parentTaskId == null }
        val pendingTasks = topTasks.filter { !it.isCompleted }
        val routinesToday = routineDao.getActiveRoutinesSync().filter { routineAppliesOn(it, today) }
        val pendingRoutines = routinesToday.filter { todayState.routineState(it, today) == "PENDING" }

        val lines = mutableListOf<String>()
        val title: String
        if (isMorning) {
            if (events.isEmpty() && topTasks.isEmpty() && routinesToday.isEmpty()) return
            title = context.getString(R.string.s2_summary_morning_title)
            events.take(4).forEach { lines.add(eventLine(it)) }
            lines.add(context.getString(R.string.s2_summary_counts, pendingTasks.size, routinesToday.size))
        } else {
            val streak = currentStreak(dayRecordsToStatuses(recordDao.getAllDayRecordsSync()), todayState.globalState(today))
            if (pendingTasks.isNotEmpty() || pendingRoutines.isNotEmpty()) {
                title = context.getString(R.string.s2_summary_evening_title)
                pendingTasks.take(4).forEach { lines.add(it.title) }
                pendingRoutines.take(3).forEach { lines.add(it.name) }
                lines.add(context.getString(R.string.s2_summary_streak, streak))
            } else if (topTasks.isNotEmpty() || routinesToday.isNotEmpty()) {
                title = context.getString(R.string.s2_summary_done_title)
                lines.add(context.getString(R.string.s2_summary_done_text, streak))
            } else {
                return
            }
        }

        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val id = if (isMorning) NotificationIds.SUMMARY_MORNING else NotificationIds.SUMMARY_EVENING
        val builder = NotificationCompat.Builder(context, NotificationChannels.SUMMARIES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(openIntent(id, "calendar"))
            .setAutoCancel(true)
        manager.notify(id, builder.build())
    }
}
