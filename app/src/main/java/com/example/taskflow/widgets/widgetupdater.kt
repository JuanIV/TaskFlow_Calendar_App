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
        try {
            TodayTasksWidget().updateAll(context)
        } catch (e: Exception) {
            // sin widgets colocados o error de Glance: no debe afectar al resto
        }
        try {
            AgendaWidget().updateAll(context)
        } catch (e: Exception) {
        }
    }
}
