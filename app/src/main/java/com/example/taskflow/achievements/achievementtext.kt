package com.example.taskflow.achievements

import com.example.taskflow.R

fun achievementNameRes(id: String): Int = when (id) {
    "TASKS_10" -> R.string.s3_ach_name_tasks_10
    "TASKS_50" -> R.string.s3_ach_name_tasks_50
    "TASKS_100" -> R.string.s3_ach_name_tasks_100
    "TASKS_500" -> R.string.s3_ach_name_tasks_500
    "STREAK_3" -> R.string.s3_ach_name_streak_3
    "STREAK_7" -> R.string.s3_ach_name_streak_7
    "STREAK_30" -> R.string.s3_ach_name_streak_30
    "STREAK_100" -> R.string.s3_ach_name_streak_100
    "FOCUS_60" -> R.string.s3_ach_name_focus_60
    "FOCUS_600" -> R.string.s3_ach_name_focus_600
    "FOCUS_3000" -> R.string.s3_ach_name_focus_3000
    "DAYS_10" -> R.string.s3_ach_name_days_10
    "DAYS_50" -> R.string.s3_ach_name_days_50
    "DAYS_100" -> R.string.s3_ach_name_days_100
    "ROUTINE_1" -> R.string.s3_ach_name_routine_1
    "EVENT_1" -> R.string.s3_ach_name_event_1
    else -> R.string.s3_ach_name_unknown
}

fun achievementDescRes(metric: String): Int = when (metric) {
    "TASKS" -> R.string.s3_ach_d_tasks
    "STREAK" -> R.string.s3_ach_d_streak
    "FOCUS" -> R.string.s3_ach_d_focus
    "DAYS" -> R.string.s3_ach_d_days
    "ROUTINES" -> R.string.s3_ach_d_routines
    else -> R.string.s3_ach_d_events
}
