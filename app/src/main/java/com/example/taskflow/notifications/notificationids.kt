package com.example.taskflow.notifications

object NotificationIds {
    const val TODAY_TASKS = 1
    const val SUMMARY_MORNING = 100
    const val SUMMARY_EVENING = 101
    const val LOOSE_TASKS = 102
    const val ALERT_BASE = 10_000
    const val ROUTINE_BASE = 20_000
    const val ROUTINE_RANGE = 9_999
    const val EVENT_BASE = 1_000_000
    const val TASK_REMINDER_BASE = 2_000_000

    fun routine(routineId: Long): Int = ROUTINE_BASE + (routineId.toInt() % ROUTINE_RANGE)
    fun alert(routineId: Long): Int = ALERT_BASE + (routineId.toInt() % ROUTINE_RANGE)
    fun event(eventId: Long, occurrenceDate: Long): Int =
        EVENT_BASE + (("ev:$eventId:$occurrenceDate").hashCode() and 0xFFFFF)
    fun taskReminder(taskId: Long): Int = TASK_REMINDER_BASE + (taskId.toInt() and 0xFFFFF)
}

object NotificationActions {
    const val TOGGLE_TASK = "ACTION_TOGGLE_TASK"
    const val COMPLETE_ALL_TASKS = "ACTION_COMPLETE_ALL_TASKS"
    const val UNDO_TASKS = "ACTION_UNDO_TASKS"
    const val POSTPONE_TODAY = "ACTION_POSTPONE_TODAY"
    const val TOGGLE_ROUTINE_TASK = "ACTION_TOGGLE_ROUTINE_TASK"
    const val COMPLETE_ALL_ROUTINE = "ACTION_COMPLETE_ALL_ROUTINE"
    const val COMPLETE_ONE_TASK = "ACTION_COMPLETE_ONE_TASK"
    const val POSTPONE_ONE_TASK = "ACTION_POSTPONE_ONE_TASK"
    const val DISMISS_TASKS = "ACTION_DISMISS_TASKS"
    const val DISMISS_ROUTINE = "ACTION_DISMISS_ROUTINE"

    const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
    const val EXTRA_ROUTINE_TASK_ID = "EXTRA_ROUTINE_TASK_ID"
    const val EXTRA_ROUTINE_ID = "EXTRA_ROUTINE_ID"
}
