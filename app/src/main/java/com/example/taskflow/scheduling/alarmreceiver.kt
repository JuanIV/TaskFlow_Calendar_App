package com.example.taskflow.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.DayCloser
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.focus.FocusManager
import com.example.taskflow.notifications.NotificationRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: SchedulingCoordinator
    @Inject lateinit var refresher: NotificationRefresher
    @Inject lateinit var dayCloser: DayCloser
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var focusManager: FocusManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    AlarmContract.ACTION_DAY_CHANGE -> {
                        val dayStart = settingsRepo.getDayStartHourSync()
                        dayCloser.closeDaysUpTo(logicalDateNow(dayStart) - 1)
                        coordinator.rescheduleAll()
                        coordinator.refreshNotifications()
                    }
                    AlarmContract.ACTION_TODAY_TASKS,
                    AlarmContract.ACTION_ROUTINE_START,
                    AlarmContract.ACTION_ROUTINE_END -> {
                        coordinator.refreshNotifications()
                        coordinator.rescheduleAll()
                    }
                    AlarmContract.ACTION_EVENT_REMINDER -> {
                        val eventId = intent.getLongExtra(AlarmContract.EXTRA_EVENT_ID, -1L)
                        val occDate = intent.getLongExtra(AlarmContract.EXTRA_OCCURRENCE_DATE, -1L)
                        if (eventId != -1L && occDate != -1L) refresher.showEventReminder(eventId, occDate)
                        coordinator.rescheduleAll()
                    }
                    AlarmContract.ACTION_TASK_REMINDER -> {
                        val taskId = intent.getLongExtra(AlarmContract.EXTRA_TASK_ID, -1L)
                        if (taskId != -1L) refresher.showTaskReminder(taskId)
                        coordinator.rescheduleAll()
                    }
                    AlarmContract.ACTION_ROUTINE_END_WARNING -> {
                        val routineId = intent.getLongExtra(AlarmContract.EXTRA_ROUTINE_ID, -1L)
                        if (routineId != -1L) refresher.showRoutineEndWarning(routineId)
                        coordinator.rescheduleAll()
                    }
                    AlarmContract.ACTION_SUMMARY_MORNING -> {
                        refresher.showSummary(true)
                        coordinator.rescheduleAll()
                    }
                    AlarmContract.ACTION_SUMMARY_EVENING -> {
                        refresher.showSummary(false)
                        coordinator.rescheduleAll()
                    }
                    AlarmContract.ACTION_FOCUS_END -> {
                        focusManager.onAlarm()
                    }
                    AlarmContract.ACTION_LOOSE_REMINDER -> {
                        refresher.showLooseReminder()
                        coordinator.rescheduleAll()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
