package com.example.taskflow.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.RoutineTaskCompletionEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.TaskCompletionHelper
import com.example.taskflow.domain.canToggleInSequence
import com.example.taskflow.domain.getTodayTasksForNotification
import com.example.taskflow.domain.isProjectDateLocked
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.orderForSequence
import com.example.taskflow.domain.taskAppliesOn
import com.example.taskflow.scheduling.SchedulingCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskActionReceiver : BroadcastReceiver() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var routineDao: RoutineDao
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var coordinator: SchedulingCoordinator
    @Inject lateinit var completionHelper: TaskCompletionHelper

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dayStart = settingsRepo.getDayStartHourSync()
                val todayEpoch = logicalDateNow(dayStart)
                val nowMs = System.currentTimeMillis()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                var refreshOnly = false

                when (action) {
                    NotificationActions.TOGGLE_TASK -> {
                        val task = taskDao.getTaskById(intent.getLongExtra(NotificationActions.EXTRA_TASK_ID, -1L))
                        if (task != null) {
                            if (task.isCompleted) completionHelper.uncomplete(task) else completionHelper.complete(task, nowMs)
                        }
                    }
                    NotificationActions.COMPLETE_ALL_TASKS -> {
                        val overdueMode = settingsRepo.overdueModeFlow.first()
                        val pending = getTodayTasksForNotification(taskDao.getTasksUpToDateSync(todayEpoch), todayEpoch, overdueMode)
                        if (pending.isNotEmpty()) {
                            settingsRepo.setUndoBatch(pending.joinToString(",") { it.id.toString() }, "")
                            settingsRepo.setUndoExpires(nowMs + 5 * 60 * 1000L)
                            pending.forEach { completionHelper.complete(it, nowMs) }
                        }
                    }
                    NotificationActions.UNDO_TASKS -> {
                        val ids = settingsRepo.getUndoBatchSync().first.split(",").mapNotNull { it.toLongOrNull() }
                        ids.forEach { id ->
                            val task = taskDao.getTaskById(id)
                            if (task != null) completionHelper.uncomplete(task)
                        }
                        settingsRepo.setUndoBatch("", "")
                        settingsRepo.setUndoExpires(0L)
                    }
                    NotificationActions.POSTPONE_TODAY -> {
                        val overdueMode = settingsRepo.overdueModeFlow.first()
                        val pending = getTodayTasksForNotification(taskDao.getTasksUpToDateSync(todayEpoch), todayEpoch, overdueMode)
                        pending.filter { !it.isProjectDateLocked() }.forEach {
                            taskDao.updateTask(it.copy(date = todayEpoch + 1, originalDate = it.originalDate ?: it.date))
                        }
                    }
                    NotificationActions.TOGGLE_ROUTINE_TASK -> {
                        val id = intent.getLongExtra(NotificationActions.EXTRA_ROUTINE_TASK_ID, -1L)
                        if (id != -1L) {
                            val completionsToday = routineDao.getAllCompletionsOnDateSync(todayEpoch)
                            val done = completionsToday.any { it.routineTaskId == id }
                            val owner = routineDao.getActiveRoutinesSync().firstOrNull { r ->
                                routineDao.getTasksForRoutineSync(r.id).any { it.id == id }
                            }
                            val allowed = owner == null || canToggleInSequence(
                                owner.sequentialMode,
                                orderForSequence(routineDao.getTasksForRoutineSync(owner.id).filter { taskAppliesOn(it, todayEpoch) }),
                                completionsToday.map { it.routineTaskId }.toSet(),
                                id
                            )
                            if (!allowed) Unit
                            else if (done) routineDao.deleteTaskCompletion(id, todayEpoch)
                            else routineDao.insertTaskCompletion(RoutineTaskCompletionEntity(id, todayEpoch, nowMs))
                        }
                    }
                    NotificationActions.COMPLETE_ALL_ROUTINE -> {
                        val routineId = intent.getLongExtra(NotificationActions.EXTRA_ROUTINE_ID, -1L)
                        routineDao.getTasksForRoutineSync(routineId)
                            .filter { taskAppliesOn(it, todayEpoch) }
                            .forEach { routineDao.insertTaskCompletion(RoutineTaskCompletionEntity(it.id, todayEpoch, nowMs)) }
                    }
                    NotificationActions.COMPLETE_ONE_TASK -> {
                        val id = intent.getLongExtra(NotificationActions.EXTRA_TASK_ID, -1L)
                        val task = taskDao.getTaskById(id)
                        if (task != null) completionHelper.complete(task, nowMs)
                        notificationManager.cancel(NotificationIds.taskReminder(id))
                    }
                    NotificationActions.POSTPONE_ONE_TASK -> {
                        val id = intent.getLongExtra(NotificationActions.EXTRA_TASK_ID, -1L)
                        val task = taskDao.getTaskById(id)
                        if (task != null && task.date != null && !task.isProjectDateLocked()) {
                            taskDao.updateTask(task.copy(date = todayEpoch + 1, originalDate = task.originalDate ?: task.date))
                        }
                        notificationManager.cancel(NotificationIds.taskReminder(id))
                    }
                    NotificationActions.DISMISS_TASKS,
                    NotificationActions.DISMISS_ROUTINE -> refreshOnly = true
                }

                if (!refreshOnly) coordinator.rescheduleAll()
                coordinator.refreshNotifications()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
