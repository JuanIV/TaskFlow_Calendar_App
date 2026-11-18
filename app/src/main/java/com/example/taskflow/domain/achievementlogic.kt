package com.example.taskflow.domain

data class AchievementStats(
    val tasksCompleted: Int,
    val bestStreak: Int,
    val focusMinutes: Int,
    val perfectDays: Int,
    val routines: Int,
    val events: Int
)

data class AchievementDef(val id: String, val metric: String, val target: Int)

val ACHIEVEMENT_DEFS = listOf(
    AchievementDef("TASKS_10", "TASKS", 10),
    AchievementDef("TASKS_50", "TASKS", 50),
    AchievementDef("TASKS_100", "TASKS", 100),
    AchievementDef("TASKS_500", "TASKS", 500),
    AchievementDef("STREAK_3", "STREAK", 3),
    AchievementDef("STREAK_7", "STREAK", 7),
    AchievementDef("STREAK_30", "STREAK", 30),
    AchievementDef("STREAK_100", "STREAK", 100),
    AchievementDef("FOCUS_60", "FOCUS", 60),
    AchievementDef("FOCUS_600", "FOCUS", 600),
    AchievementDef("FOCUS_3000", "FOCUS", 3000),
    AchievementDef("DAYS_10", "DAYS", 10),
    AchievementDef("DAYS_50", "DAYS", 50),
    AchievementDef("DAYS_100", "DAYS", 100),
    AchievementDef("ROUTINE_1", "ROUTINES", 1),
    AchievementDef("EVENT_1", "EVENTS", 1)
)

fun achievementMetric(stats: AchievementStats, metric: String): Int = when (metric) {
    "TASKS" -> stats.tasksCompleted
    "STREAK" -> stats.bestStreak
    "FOCUS" -> stats.focusMinutes
    "DAYS" -> stats.perfectDays
    "ROUTINES" -> stats.routines
    "EVENTS" -> stats.events
    else -> 0
}

fun unlockedAchievementIds(stats: AchievementStats): Set<String> =
    ACHIEVEMENT_DEFS.filter { achievementMetric(stats, it.metric) >= it.target }.map { it.id }.toSet()
