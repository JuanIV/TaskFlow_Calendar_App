package com.example.taskflow.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.taskflow.domain.FocusState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DAY_START_HOUR = intPreferencesKey("day_start_hour")
    private val OVERDUE_MODE = stringPreferencesKey("overdue_mode")
    private val LAST_CLOSED_DAY = longPreferencesKey("last_closed_day")
    private val TODAY_TASKS_MINUTE = intPreferencesKey("today_tasks_minute")
    private val MORNING_ENABLED = booleanPreferencesKey("morning_enabled")
    private val MORNING_MINUTE = intPreferencesKey("morning_minute")
    private val EVENING_ENABLED = booleanPreferencesKey("evening_enabled")
    private val EVENING_MINUTE = intPreferencesKey("evening_minute")
    private val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
    private val QUIET_START = intPreferencesKey("quiet_start")
    private val QUIET_END = intPreferencesKey("quiet_end")
    private val UNDO_BATCH_TASKS = stringPreferencesKey("undo_batch_tasks")
    private val UNDO_BATCH_ROUTINES = stringPreferencesKey("undo_batch_routines")
    private val UNDO_EXPIRES = longPreferencesKey("undo_batch_expires_at")
    private val SCHEDULED_ALARMS = stringSetPreferencesKey("scheduled_alarms")
    private val LOOSE_ENABLED = booleanPreferencesKey("loose_reminder_enabled")
    private val LOOSE_INTERVAL = intPreferencesKey("loose_reminder_interval_hours")
    private val LOOSE_START = intPreferencesKey("loose_reminder_window_start")
    private val LOOSE_END = intPreferencesKey("loose_reminder_window_end")
    private val CALENDAR_VIEW = stringPreferencesKey("calendar_view")
    private val TASK_SORT_MODE = stringPreferencesKey("task_sort_mode")

    val dayStartHourFlow: Flow<Int> = context.dataStore.data.map { it[DAY_START_HOUR] ?: 0 }
    val overdueModeFlow: Flow<String> = context.dataStore.data.map { it[OVERDUE_MODE] ?: "SHOW" }
    val lastClosedDayFlow: Flow<Long> = context.dataStore.data.map { it[LAST_CLOSED_DAY] ?: 0L }
    val todayTasksMinuteFlow: Flow<Int> = context.dataStore.data.map { it[TODAY_TASKS_MINUTE] ?: (7 * 60) }
    val morningEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[MORNING_ENABLED] ?: true }
    val morningMinuteFlow: Flow<Int> = context.dataStore.data.map { it[MORNING_MINUTE] ?: (8 * 60) }
    val eveningEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[EVENING_ENABLED] ?: true }
    val eveningMinuteFlow: Flow<Int> = context.dataStore.data.map { it[EVENING_MINUTE] ?: (21 * 60 + 30) }
    val quietEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[QUIET_ENABLED] ?: false }
    val quietStartFlow: Flow<Int> = context.dataStore.data.map { it[QUIET_START] ?: (23 * 60) }
    val quietEndFlow: Flow<Int> = context.dataStore.data.map { it[QUIET_END] ?: (7 * 60) }
    val looseEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[LOOSE_ENABLED] ?: true }
    val looseIntervalFlow: Flow<Int> = context.dataStore.data.map { it[LOOSE_INTERVAL] ?: 6 }
    val looseStartFlow: Flow<Int> = context.dataStore.data.map { it[LOOSE_START] ?: (9 * 60) }
    val looseEndFlow: Flow<Int> = context.dataStore.data.map { it[LOOSE_END] ?: (21 * 60) }
    val calendarViewFlow: Flow<String> = context.dataStore.data.map { it[CALENDAR_VIEW] ?: "MONTH" }
    val taskSortModeFlow: Flow<String> = context.dataStore.data.map { it[TASK_SORT_MODE] ?: "MANUAL" }

    suspend fun getDayStartHourSync(): Int = dayStartHourFlow.first()
    suspend fun getLastClosedDaySync(): Long = lastClosedDayFlow.first()
    suspend fun getTodayTasksMinuteSync(): Int = todayTasksMinuteFlow.first()

    suspend fun setDayStartHour(hour: Int) { context.dataStore.edit { it[DAY_START_HOUR] = hour } }
    suspend fun setOverdueMode(mode: String) { context.dataStore.edit { it[OVERDUE_MODE] = mode } }
    suspend fun setLastClosedDay(epochDay: Long) { context.dataStore.edit { it[LAST_CLOSED_DAY] = epochDay } }
    suspend fun setTodayTasksMinute(m: Int) { context.dataStore.edit { it[TODAY_TASKS_MINUTE] = m } }
    suspend fun setMorningSummary(enabled: Boolean, minute: Int) { context.dataStore.edit { it[MORNING_ENABLED] = enabled; it[MORNING_MINUTE] = minute } }
    suspend fun setMorningEnabled(enabled: Boolean) { context.dataStore.edit { it[MORNING_ENABLED] = enabled } }
    suspend fun setEveningSummary(enabled: Boolean, minute: Int) { context.dataStore.edit { it[EVENING_ENABLED] = enabled; it[EVENING_MINUTE] = minute } }
    suspend fun setQuietHours(enabled: Boolean, start: Int, end: Int) { context.dataStore.edit { it[QUIET_ENABLED] = enabled; it[QUIET_START] = start; it[QUIET_END] = end } }
    suspend fun setLooseReminder(enabled: Boolean, intervalHours: Int, start: Int, end: Int) {
        context.dataStore.edit {
            it[LOOSE_ENABLED] = enabled
            it[LOOSE_INTERVAL] = intervalHours
            it[LOOSE_START] = start
            it[LOOSE_END] = end
        }
    }
    suspend fun setCalendarView(view: String) { context.dataStore.edit { it[CALENDAR_VIEW] = view } }
    suspend fun setTaskSortMode(mode: String) { context.dataStore.edit { it[TASK_SORT_MODE] = mode } }

    suspend fun setUndoBatch(tasks: String, routines: String) {
        context.dataStore.edit { it[UNDO_BATCH_TASKS] = tasks; it[UNDO_BATCH_ROUTINES] = routines }
    }
    suspend fun getUndoBatchSync(): Pair<String, String> {
        val prefs = context.dataStore.data.first()
        return Pair(prefs[UNDO_BATCH_TASKS] ?: "", prefs[UNDO_BATCH_ROUTINES] ?: "")
    }
    suspend fun setUndoExpires(epochMs: Long) { context.dataStore.edit { it[UNDO_EXPIRES] = epochMs } }
    suspend fun getUndoExpiresSync(): Long = context.dataStore.data.first()[UNDO_EXPIRES] ?: 0L

    suspend fun getScheduledAlarmsSync(): Set<String> {
        val prefs = context.dataStore.data.first()
        return prefs[SCHEDULED_ALARMS] ?: emptySet()
    }
    suspend fun setScheduledAlarms(keys: Set<String>) {
        context.dataStore.edit { it[SCHEDULED_ALARMS] = keys }
    }

    // ---------------------------------------------------------------
    // Fase 3
    // ---------------------------------------------------------------
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val ACCENT_COLOR = intPreferencesKey("accent_color")
    private val FIRST_DAY = intPreferencesKey("first_day_of_week")
    private val TIME_FORMAT = stringPreferencesKey("time_format")
    private val CUSTOM_PALETTE = stringPreferencesKey("custom_palette")
    private val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
    private val LOCK_TIMEOUT = intPreferencesKey("lock_timeout_sec")
    private val WEEKLY_ENABLED = booleanPreferencesKey("weekly_enabled")
    private val WEEKLY_DAY = intPreferencesKey("weekly_day")
    private val WEEKLY_MINUTE = intPreferencesKey("weekly_minute")
    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val POMO_FOCUS = intPreferencesKey("pomodoro_focus")
    private val POMO_SHORT = intPreferencesKey("pomodoro_short")
    private val POMO_LONG = intPreferencesKey("pomodoro_long")
    private val POMO_CYCLES = intPreferencesKey("pomodoro_cycles")
    private val FOCUS_ACTIVE = booleanPreferencesKey("focus_active")
    private val FOCUS_MODE = stringPreferencesKey("focus_mode")
    private val FOCUS_PHASE = stringPreferencesKey("focus_phase")
    private val FOCUS_TASK = longPreferencesKey("focus_task_id")
    private val FOCUS_START = longPreferencesKey("focus_start")
    private val FOCUS_END = longPreferencesKey("focus_end")
    private val FOCUS_CYCLE = intPreferencesKey("focus_cycle")
    private val FOCUS_PROJECT_NODE = longPreferencesKey("focus_project_node")
    private val EXT_ENABLED = booleanPreferencesKey("ext_calendar_enabled")
    private val EXT_IDS = stringSetPreferencesKey("ext_calendar_ids")
    private val ACHIEVEMENTS = stringSetPreferencesKey("achievements_unlocked")
    private val SYNC_FOLDER = stringPreferencesKey("sync_folder_uri")
    private val ALARM_SNOOZE = intPreferencesKey("alarm_snooze_minutes")
    private fun projectRiskKey(projectId: Long) = stringPreferencesKey("project_risk_$projectId")

    val themeModeFlow: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "SYSTEM" }
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val accentColorFlow: Flow<Int> = context.dataStore.data.map { it[ACCENT_COLOR] ?: 0xFF3949AB.toInt() }
    val firstDayFlow: Flow<Int> = context.dataStore.data.map { it[FIRST_DAY] ?: 1 }
    val timeFormatFlow: Flow<String> = context.dataStore.data.map { it[TIME_FORMAT] ?: "SYSTEM" }
    val customPaletteFlow: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        (prefs[CUSTOM_PALETTE] ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }
    }
    val lockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[LOCK_ENABLED] ?: false }
    val lockTimeoutFlow: Flow<Int> = context.dataStore.data.map { it[LOCK_TIMEOUT] ?: 30 }
    val weeklyEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[WEEKLY_ENABLED] ?: true }
    val weeklyDayFlow: Flow<Int> = context.dataStore.data.map { it[WEEKLY_DAY] ?: 7 }
    val weeklyMinuteFlow: Flow<Int> = context.dataStore.data.map { it[WEEKLY_MINUTE] ?: (20 * 60) }
    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }
    val pomodoroFocusFlow: Flow<Int> = context.dataStore.data.map { it[POMO_FOCUS] ?: 25 }
    val pomodoroShortFlow: Flow<Int> = context.dataStore.data.map { it[POMO_SHORT] ?: 5 }
    val pomodoroLongFlow: Flow<Int> = context.dataStore.data.map { it[POMO_LONG] ?: 15 }
    val pomodoroCyclesFlow: Flow<Int> = context.dataStore.data.map { it[POMO_CYCLES] ?: 4 }
    val focusStateFlow: Flow<FocusState> = context.dataStore.data.map { p ->
        FocusState(
            active = p[FOCUS_ACTIVE] ?: false,
            mode = p[FOCUS_MODE] ?: "POMODORO",
            phase = p[FOCUS_PHASE] ?: "FOCUS",
            taskId = (p[FOCUS_TASK] ?: -1L).takeIf { it > 0L },
            startMs = p[FOCUS_START] ?: 0L,
            endMs = p[FOCUS_END] ?: 0L,
            cycle = p[FOCUS_CYCLE] ?: 0,
            projectNodeId = (p[FOCUS_PROJECT_NODE] ?: -1L).takeIf { it > 0L }
        )
    }
    val extCalendarEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[EXT_ENABLED] ?: false }
    val extCalendarIdsFlow: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        (prefs[EXT_IDS] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    }
    val achievementsFlow: Flow<Set<String>> = context.dataStore.data.map { it[ACHIEVEMENTS] ?: emptySet() }
    val syncFolderFlow: Flow<String> = context.dataStore.data.map { it[SYNC_FOLDER] ?: "" }
    val alarmSnoozeFlow: Flow<Int> = context.dataStore.data.map { it[ALARM_SNOOZE] ?: 10 }

    suspend fun setThemeMode(value: String) { context.dataStore.edit { it[THEME_MODE] = value } }
    suspend fun setDynamicColor(value: Boolean) { context.dataStore.edit { it[DYNAMIC_COLOR] = value } }
    suspend fun setAccentColor(value: Int) { context.dataStore.edit { it[ACCENT_COLOR] = value } }
    suspend fun setFirstDay(value: Int) { context.dataStore.edit { it[FIRST_DAY] = value } }
    suspend fun setTimeFormat(value: String) { context.dataStore.edit { it[TIME_FORMAT] = value } }
    suspend fun setCustomPalette(list: List<Int>) { context.dataStore.edit { it[CUSTOM_PALETTE] = list.joinToString(",") } }
    suspend fun setLock(enabled: Boolean, timeoutSec: Int) {
        context.dataStore.edit { it[LOCK_ENABLED] = enabled; it[LOCK_TIMEOUT] = timeoutSec }
    }
    suspend fun setWeekly(enabled: Boolean, day: Int, minute: Int) {
        context.dataStore.edit { it[WEEKLY_ENABLED] = enabled; it[WEEKLY_DAY] = day; it[WEEKLY_MINUTE] = minute }
    }
    suspend fun setOnboardingDone(value: Boolean) { context.dataStore.edit { it[ONBOARDING_DONE] = value } }
    suspend fun setPomodoro(focus: Int, short: Int, long: Int, cycles: Int) {
        context.dataStore.edit {
            it[POMO_FOCUS] = focus
            it[POMO_SHORT] = short
            it[POMO_LONG] = long
            it[POMO_CYCLES] = cycles
        }
    }
    suspend fun setFocusState(state: FocusState) {
        context.dataStore.edit {
            it[FOCUS_ACTIVE] = state.active
            it[FOCUS_MODE] = state.mode
            it[FOCUS_PHASE] = state.phase
            it[FOCUS_TASK] = state.taskId ?: -1L
            it[FOCUS_START] = state.startMs
            it[FOCUS_END] = state.endMs
            it[FOCUS_CYCLE] = state.cycle
            it[FOCUS_PROJECT_NODE] = state.projectNodeId ?: -1L
        }
    }
    suspend fun setExtCalendars(enabled: Boolean, ids: Set<Long>) {
        context.dataStore.edit {
            it[EXT_ENABLED] = enabled
            it[EXT_IDS] = ids.map { id -> id.toString() }.toSet()
        }
    }
    suspend fun addAchievement(entry: String) {
        context.dataStore.edit { it[ACHIEVEMENTS] = (it[ACHIEVEMENTS] ?: emptySet()) + entry }
    }
    suspend fun setSyncFolder(uri: String) { context.dataStore.edit { it[SYNC_FOLDER] = uri } }
    suspend fun setAlarmSnooze(minutes: Int) { context.dataStore.edit { it[ALARM_SNOOZE] = minutes } }

    suspend fun getProjectRiskSync(projectId: Long): String? =
        context.dataStore.data.first()[projectRiskKey(projectId)]

    suspend fun setProjectRisk(projectId: Long, status: String) {
        context.dataStore.edit { it[projectRiskKey(projectId)] = status }
    }
}
