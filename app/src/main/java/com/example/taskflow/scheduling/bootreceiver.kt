package com.example.taskflow.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.DayCloser
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.notifications.NotificationRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: SchedulingCoordinator
    @Inject lateinit var dayCloser: DayCloser
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var refresher: NotificationRefresher

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )

        if (intent.action !in validActions) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dayStartHour = settingsRepo.getDayStartHourSync()
                val logicalYesterday = logicalDateNow(dayStartHour) - 1
                dayCloser.closeDaysUpTo(logicalYesterday)
                coordinator.rescheduleAll()
                refresher.refreshAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
