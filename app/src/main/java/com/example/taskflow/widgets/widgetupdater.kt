package com.example.taskflow.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun updateAll() {
        try { TodayTasksWidget().updateAll(context) } catch (e: Exception) { }
        try { AgendaWidget().updateAll(context) } catch (e: Exception) { }
        try { RoutinesWidget().updateAll(context) } catch (e: Exception) { }
        try { StreakWidget().updateAll(context) } catch (e: Exception) { }
        try { FocusWidget().updateAll(context) } catch (e: Exception) { }
        try { MonthWidget().updateAll(context) } catch (e: Exception) { }
        try { WeekWidget().updateAll(context) } catch (e: Exception) { }
    }
}