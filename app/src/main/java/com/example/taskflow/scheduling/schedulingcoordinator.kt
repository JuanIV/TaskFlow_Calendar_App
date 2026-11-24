package com.example.taskflow.scheduling

import android.os.Bundle
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.EventExceptionDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.daos.TaskReminderDao
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.achievements.AchievementManager
import com.example.taskflow.domain.getLogicalInstant
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.nextLooseReminderTrigger
import com.example.taskflow.domain.nextWeeklyTrigger
import com.example.taskflow.domain.nextReminderOccurrence
import com.example.taskflow.domain.routineAppliesOn
import com.example.taskflow.notifications.NotificationRefresher
import com.example.taskflow.notifications.ProjectNotifier
import com.example.taskflow.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface SchedulingCoordinator {
    suspend fun rescheduleAll()
    suspend fun refreshNotifications()
    fun onDataChanged()
}

private data class PlannedAlarm(val action: String, val extras: Bundle, val triggerAtMs: Long)

@Singleton
class SchedulingCoordinatorImpl @Inject constructor(
    private val eventDao: EventDao,
    private val exceptionDao: EventExceptionDao,
    private val routineDao: RoutineDao,
    private val taskDao: TaskDao,
    private val taskReminderDao: TaskReminderDao,
    private val settingsRepo: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notificationRefresher: NotificationRefresher,
    private val widgetUpdater: WidgetUpdater,
    private val achievementManager: AchievementManager,
    private val projectNotifier: ProjectNotifier
) : SchedulingCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    override fun onDataChanged() {
        scope.launch {
            rescheduleAll()
            refreshNotifications()
        }
    }

    override suspend fun refreshNotifications(): Unit = mutex.withLock {
        notificationRefresher.refreshAll()
        projectNotifier.refreshToday()
        projectNotifier.checkRisks()
        widgetUpdater.updateAll()
        achievementManager.check()
    }

    private fun nextDailyInstant(todayEpoch: Long, dayStart: Int, minute: Int, nowMs: Long): Long {
        var ms = getLogicalInstant(todayEpoch, dayStart, minute)
        if (ms <= nowMs) ms = getLogicalInstant(todayEpoch + 1, dayStart, minute)
        return ms
    }

    private fun firstFuture(routine: RoutineEntity, todayEpoch: Long, nowMs: Long, instantOf: (Long) -> Long): Long? {
        for (i in 0..60) {
            val day = todayEpoch + i
            if (routineAppliesOn(routine, day)) {
                val ms = instantOf(day)
                if (ms > nowMs) return ms
            }
        }
        return null
    }

    override suspend fun rescheduleAll(): Unit = mutex.withLock {
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val dayStart = settingsRepo.getDayStartHourSync()
        val todayEpoch = logicalDateNow(dayStart)
        val desired = LinkedHashMap<String, PlannedAlarm>()

        desired["daychange"] = PlannedAlarm(
            AlarmContract.ACTION_DAY_CHANGE, Bundle(),
            getLogicalInstant(todayEpoch + 1, dayStart, dayStart * 60)
        )

        desired["todaytasks"] = PlannedAlarm(
            AlarmContract.ACTION_TODAY_TASKS, Bundle(),
            nextDailyInstant(todayEpoch, dayStart, settingsRepo.todayTasksMinuteFlow.first(), nowMs)
        )

        if (settingsRepo.morningEnabledFlow.first()) {
            desired["sum_morning"] = PlannedAlarm(
                AlarmContract.ACTION_SUMMARY_MORNING, Bundle(),
                nextDailyInstant(todayEpoch, dayStart, settingsRepo.morningMinuteFlow.first(), nowMs)
            )
        }
        if (settingsRepo.eveningEnabledFlow.first()) {
            desired["sum_evening"] = PlannedAlarm(
                AlarmContract.ACTION_SUMMARY_EVENING, Bundle(),
                nextDailyInstant(todayEpoch, dayStart, settingsRepo.eveningMinuteFlow.first(), nowMs)
            )
        }

        if (settingsRepo.weeklyEnabledFlow.first()) {
            desired["sum_weekly"] = PlannedAlarm(
                AlarmContract.ACTION_SUMMARY_WEEKLY, Bundle(),
                nextWeeklyTrigger(nowMs, settingsRepo.weeklyDayFlow.first(), settingsRepo.weeklyMinuteFlow.first(), zone)
            )
        }
        for (routine in routineDao.getActiveRoutinesSync()) {
            val extras = Bundle().apply { putLong(AlarmContract.EXTRA_ROUTINE_ID, routine.id) }
            firstFuture(routine, todayEpoch, nowMs) { day -> getLogicalInstant(day, dayStart, routine.startMinuteOfDay) }
                ?.let { desired["rtstart:${routine.id}"] = PlannedAlarm(AlarmContract.ACTION_ROUTINE_START, extras, it) }

            val end = routine.endMinuteOfDay
            if (end != null) {
                firstFuture(routine, todayEpoch, nowMs) { day -> getLogicalInstant(day, dayStart, end) }
                    ?.let { desired["rtend:${routine.id}"] = PlannedAlarm(AlarmContract.ACTION_ROUTINE_END, extras, it) }

                val warn = routine.endWarningMinutes
                if (warn != null) {
                    val warnMinute = ((end - warn) % 1440 + 1440) % 1440
                    firstFuture(routine, todayEpoch, nowMs) { day -> getLogicalInstant(day, dayStart, warnMinute) }
                        ?.let { desired["rtwarn:${routine.id}"] = PlannedAlarm(AlarmContract.ACTION_ROUTINE_END_WARNING, extras, it) }
                }
            }
        }

        val activeEvents = eventDao.getActiveEventsFromSync(todayEpoch - 365)
        if (activeEvents.isNotEmpty()) {
            val eventIds = activeEvents.map { it.id }
            val reminders = eventDao.getRemindersForEvents(eventIds)
            val exceptions = exceptionDao.getExceptionsForEvents(eventIds)
            for (reminder in reminders) {
                val event = activeEvents.firstOrNull { it.id == reminder.eventId } ?: continue
                val trigger = nextReminderOccurrence(event, exceptions, reminder.offsetMinutes, nowMs) ?: continue
                val extras = Bundle().apply {
                    putLong(AlarmContract.EXTRA_EVENT_ID, event.id)
                    putLong(AlarmContract.EXTRA_OCCURRENCE_DATE, trigger.occurrenceOriginalDate)
                }
                desired["ev:${event.id}:${reminder.offsetMinutes}"] =
                    PlannedAlarm(AlarmContract.ACTION_EVENT_REMINDER, extras, trigger.triggerMs)
            }
        }

        val pendingTasks = taskDao.getPendingTopLevelSync().associateBy { it.id }
        for (r in taskReminderDao.getAllSync()) {
            val task = pendingTasks[r.taskId] ?: continue
            val absDay = r.absEpochDay
            val absMin = r.absMinuteOfDay
            val offset = r.offsetMinutes
            val taskDate = task.date
            val dueMin = task.dueMinuteOfDay
            val trigger: Long? = when {
                absDay != null && absMin != null ->
                    LocalDate.ofEpochDay(absDay).atTime(absMin / 60, absMin % 60).atZone(zone).toInstant().toEpochMilli()
                offset != null && taskDate != null -> {
                    val base = LocalDate.ofEpochDay(taskDate)
                    val startMs = if (dueMin != null) {
                        base.atTime(dueMin / 60, dueMin % 60).atZone(zone).toInstant().toEpochMilli()
                    } else {
                        base.atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    startMs - offset * 60_000L
                }
                else -> null
            }
            if (trigger != null && trigger > nowMs) {
                val extras = Bundle().apply { putLong(AlarmContract.EXTRA_TASK_ID, r.taskId) }
                desired["task:${r.taskId}:${r.id}"] = PlannedAlarm(AlarmContract.ACTION_TASK_REMINDER, extras, trigger)
            }
        }

        if (settingsRepo.looseEnabledFlow.first()) {
            val ms = nextLooseReminderTrigger(
                nowMs,
                settingsRepo.looseStartFlow.first(),
                settingsRepo.looseEndFlow.first(),
                settingsRepo.looseIntervalFlow.first(),
                zone
            )
            desired["loose"] = PlannedAlarm(AlarmContract.ACTION_LOOSE_REMINDER, Bundle(), ms)
        }

        val existing = settingsRepo.getScheduledAlarmsSync()
        val desiredTokens = desired.map { "${it.key}|${it.value.action}" }.toSet()

        for (token in existing - desiredTokens) {
            val parts = token.split("|")
            if (parts.size == 2) alarmScheduler.cancel(parts[0], parts[1])
        }
        for ((key, planned) in desired) {
            alarmScheduler.schedule(key, planned.action, planned.extras, planned.triggerAtMs)
        }
        settingsRepo.setScheduledAlarms(desiredTokens)
    }
}
