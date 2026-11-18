package com.example.taskflow.focus

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.example.taskflow.R
import com.example.taskflow.data.daos.FocusLogDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.FocusLogEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.FocusState
import com.example.taskflow.mainactivity
import com.example.taskflow.notifications.NotificationChannels
import com.example.taskflow.scheduling.AlarmContract
import com.example.taskflow.scheduling.AlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

object FocusIds {
    const val ONGOING = 4_000_001
    const val ALERT = 4_000_002
    const val ALARM_KEY = "focus_end"
    const val ACTION_STOP = "com.example.taskflow.FOCUS_STOP"
    const val ACTION_SKIP = "com.example.taskflow.FOCUS_SKIP"
}

@Singleton
class FocusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val focusLogDao: FocusLogDao,
    private val taskDao: TaskDao
) {
    private val mutex = Mutex()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val state: Flow<FocusState> = settingsRepo.focusStateFlow

    private suspend fun currentState(): FocusState = settingsRepo.focusStateFlow.first()

    private suspend fun config(): PomodoroConfig = PomodoroConfig(
        settingsRepo.pomodoroFocusFlow.first(),
        settingsRepo.pomodoroShortFlow.first(),
        settingsRepo.pomodoroLongFlow.first(),
        settingsRepo.pomodoroCyclesFlow.first()
    )

    suspend fun start(mode: String, taskId: Long?, timerMinutes: Int) = mutex.withLock {
        val current = currentState()
        if (current.active) finish(current)
        val cfg = config()
        if (mode == "TIMER") {
            begin(FocusState(true, "TIMER", "TIMER", taskId, 0L, 0L, 0), timerMinutes.coerceIn(1, 600), cfg)
        } else {
            begin(FocusState(true, "POMODORO", "FOCUS", taskId, 0L, 0L, 0), cfg.focusMin, cfg)
        }
    }

    suspend fun stop() = mutex.withLock {
        val s = currentState()
        if (s.active) finish(s)
    }

    suspend fun skip() = mutex.withLock { advance(currentState(), true) }

    suspend fun onAlarm() = mutex.withLock { advance(currentState(), false) }

    // Tras reiniciar el movil o la app: recupera la sesion si sigue en curso
    suspend fun restore() = mutex.withLock {
        try {
            val s = currentState()
            if (!s.active) return@withLock
            val now = System.currentTimeMillis()
            if (now >= s.endMs) {
                logElapsed(s, now)
                reset()
            } else {
                alarmScheduler.schedule(FocusIds.ALARM_KEY, AlarmContract.ACTION_FOCUS_END, Bundle(), s.endMs)
                postOngoing(s, config())
            }
        } catch (e: Exception) {
            // no debe afectar al resto del arranque
        }
    }

    private suspend fun advance(s: FocusState, force: Boolean) {
        if (!s.active) return
        val now = System.currentTimeMillis()
        // una alarma antigua no debe cortar la fase actual
        if (!force && now < s.endMs - 5_000L) return
        logElapsed(s, now)
        if (s.mode == "TIMER") {
            reset()
            if (!force) alert(context.getString(R.string.s3_focus_timer_done), taskTitle(s.taskId) ?: "")
            return
        }
        val cfg = config()
        val next = nextPomodoroPhase(s.phase, s.cycle, cfg)
        if (!force) {
            alert(
                context.getString(R.string.s3_focus_phase_over),
                context.getString(R.string.s3_focus_next_phase, phaseLabel(next.phase))
            )
        }
        begin(s.copy(phase = next.phase, cycle = next.cycle), phaseMinutes(next.phase, cfg), cfg)
    }

    private suspend fun begin(base: FocusState, minutes: Int, cfg: PomodoroConfig) {
        val now = System.currentTimeMillis()
        val s = base.copy(active = true, startMs = now, endMs = now + minutes * 60_000L)
        settingsRepo.setFocusState(s)
        alarmScheduler.schedule(FocusIds.ALARM_KEY, AlarmContract.ACTION_FOCUS_END, Bundle(), s.endMs)
        postOngoing(s, cfg)
    }

    private suspend fun finish(s: FocusState) {
        logElapsed(s, System.currentTimeMillis())
        reset()
    }

    private suspend fun reset() {
        alarmScheduler.cancel(FocusIds.ALARM_KEY, AlarmContract.ACTION_FOCUS_END)
        settingsRepo.setFocusState(FocusState())
        notificationManager.cancel(FocusIds.ONGOING)
    }

    private suspend fun logElapsed(s: FocusState, now: Long) {
        if (s.phase != "FOCUS" && s.phase != "TIMER") return
        val minutes = ((minOf(now, s.endMs) - s.startMs) / 60_000L).toInt()
        if (minutes >= 1) {
            focusLogDao.insert(FocusLogEntity(taskId = s.taskId, startedAt = s.startMs, durationMinutes = minutes, type = s.phase))
        }
    }

    private suspend fun taskTitle(taskId: Long?): String? = taskId?.let { taskDao.getTaskById(it)?.title }

    private fun phaseLabel(phase: String): String = when (phase) {
        "SHORT_BREAK" -> context.getString(R.string.s3_focus_phase_short)
        "LONG_BREAK" -> context.getString(R.string.s3_focus_phase_long)
        "TIMER" -> context.getString(R.string.s3_focus_phase_timer)
        else -> context.getString(R.string.s3_focus_phase_focus)
    }

    private suspend fun postOngoing(s: FocusState, cfg: PomodoroConfig) {
        val taskName = taskTitle(s.taskId)
        val label = phaseLabel(s.phase)
        val title = if (taskName != null) label + " - " + taskName else label
        val cycles = cfg.cycles.coerceAtLeast(1)
        val builder = NotificationCompat.Builder(context, NotificationChannels.FOCUS_RUNNING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(s.endMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openIntent())
            .addAction(0, context.getString(R.string.s3_focus_notif_stop), actionIntent(FocusIds.ACTION_STOP, 1))
        if (s.mode == "POMODORO") {
            builder.setContentText(context.getString(R.string.s3_focus_cycle, (s.cycle % cycles) + 1, cycles))
            builder.addAction(0, context.getString(R.string.s3_focus_notif_skip), actionIntent(FocusIds.ACTION_SKIP, 2))
        }
        notificationManager.notify(FocusIds.ONGOING, builder.build())
    }

    private fun alert(title: String, text: String) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.FOCUS_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent())
            .build()
        notificationManager.notify(FocusIds.ALERT, notification)
    }

    private fun openIntent(): PendingIntent {
        val intent = Intent(context, mainactivity::class.java).apply {
            putExtra(AlarmContract.EXTRA_OPEN_ROUTE, "focus")
            data = Uri.parse("taskflow://open/focus")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 4001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(action: String, code: Int): PendingIntent {
        val intent = Intent(context, FocusActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(context, 4100 + code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
