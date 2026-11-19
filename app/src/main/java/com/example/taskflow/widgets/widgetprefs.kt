package com.example.taskflow.widgets

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

// Claves de la configuracion de cada widget (se guardan por widget)
object WidgetPrefs {
    val TODAY_SHOW_DONE = booleanPreferencesKey("today_show_done")
    val TODAY_SHOW_OVERDUE = booleanPreferencesKey("today_show_overdue")
    val TODAY_MAX = intPreferencesKey("today_max")
    val AGENDA_DAYS = intPreferencesKey("agenda_days")
    val AGENDA_TASKS = booleanPreferencesKey("agenda_tasks")
    val ROUTINES_ONLY_PENDING = booleanPreferencesKey("routines_only_pending")
    val STREAK_SHOW_BEST = booleanPreferencesKey("streak_show_best")
    val FOCUS_SHOW_TOTAL = booleanPreferencesKey("focus_show_total")
    val MONTH_MARK_EVENTS = booleanPreferencesKey("month_mark_events")
    val MONTH_MARK_TASKS = booleanPreferencesKey("month_mark_tasks")
}