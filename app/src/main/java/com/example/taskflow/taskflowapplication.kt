package com.example.taskflow

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.DayCloser
import com.example.taskflow.domain.TimeFormatState
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.focus.FocusManager
import com.example.taskflow.notifications.NotificationChannels
import com.example.taskflow.scheduling.SchedulingCoordinator
import com.example.taskflow.workers.TrashWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class taskflowapplication : Application() {

    @Inject lateinit var dayCloser: DayCloser
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var coordinator: SchedulingCoordinator
    @Inject lateinit var focusManager: FocusManager

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val dayStart = settingsRepo.getDayStartHourSync()
            dayCloser.closeDaysUpTo(logicalDateNow(dayStart) - 1)
            coordinator.rescheduleAll()
            coordinator.refreshNotifications()
            focusManager.restore()
        }

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settingsRepo.timeFormatFlow.collect { mode ->
                TimeFormatState.use24h = when (mode) {
                    "H24" -> true
                    "H12" -> false
                    else -> android.text.format.DateFormat.is24HourFormat(this@taskflowapplication)
                }
            }
        }

        val trashWork = PeriodicWorkRequestBuilder<TrashWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TrashMaintenance",
            ExistingPeriodicWorkPolicy.KEEP,
            trashWork
        )
    }
}
