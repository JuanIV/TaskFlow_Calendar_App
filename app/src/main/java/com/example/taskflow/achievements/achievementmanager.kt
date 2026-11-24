package com.example.taskflow.achievements

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.taskflow.R
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.FocusLogDao
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.data.repositories.ProjectRepository
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.ACHIEVEMENT_DEFS
import com.example.taskflow.domain.AchievementStats
import com.example.taskflow.domain.bestStreak
import com.example.taskflow.domain.dayRecordsToStatuses
import com.example.taskflow.domain.unlockedAchievementIds
import com.example.taskflow.mainactivity
import com.example.taskflow.notifications.NotificationChannels
import com.example.taskflow.scheduling.AlarmContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val eventDao: EventDao,
    private val routineDao: RoutineDao,
    private val recordDao: RecordDao,
    private val focusLogDao: FocusLogDao,
    private val settingsRepo: SettingsRepository,
    private val projectRepository: ProjectRepository
) {
    private val mutex = Mutex()

    suspend fun computeStats(): AchievementStats {
        val tasks = taskDao.getAllActiveTasksSync().count { it.parentTaskId == null && it.isCompleted }
        val records = recordDao.getAllDayRecordsSync()
        val completedProjects = projectRepository.completedProjectsSummary(
            logicalDateNow(settingsRepo.getDayStartHourSync())
        )
        return AchievementStats(
            tasksCompleted = tasks,
            bestStreak = bestStreak(dayRecordsToStatuses(records), "PENDING"),
            focusMinutes = focusLogDao.totalFocusMinutes(),
            perfectDays = records.count { it.status == "COMPLETED" },
            routines = routineDao.getAllRoutinesSync().size,
            events = eventDao.getActiveEventsFromSync(0L).size,
            projects = completedProjects.size,
            projectsEarly = completedProjects.count { it.second }
        )
    }

    suspend fun check() {
        mutex.withLock {
            try {
                val reached = unlockedAchievementIds(computeStats())
                val known = settingsRepo.achievementsFlow.first().map { it.substringBefore("|") }.toSet()
                val fresh = ACHIEVEMENT_DEFS.map { it.id }.filter { it in reached && it !in known }
                if (fresh.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    fresh.forEach { settingsRepo.addAchievement(it + "|" + now) }
                    notifyUnlocked(fresh)
                }
            } catch (e: Exception) {
                // no debe afectar al resto de la app
            }
        }
    }

    private fun notifyUnlocked(ids: List<String>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title: String
        val text: String
        if (ids.size == 1) {
            title = context.getString(R.string.s3_ach_unlocked_title)
            text = context.getString(achievementNameRes(ids[0]))
        } else {
            title = context.getString(R.string.s3_ach_unlocked_many_title)
            text = context.getString(R.string.s3_ach_unlocked_many_text, ids.size)
        }
        val intent = Intent(context, mainactivity::class.java).apply {
            putExtra(AlarmContract.EXTRA_OPEN_ROUTE, "achievements")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 5001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationChannels.ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_stat_taskflow)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(5_000_001, notification)
    }
}
