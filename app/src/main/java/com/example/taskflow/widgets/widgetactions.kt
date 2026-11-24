package com.example.taskflow.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.taskflow.capture.CaptureContract
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.mainactivity
import com.example.taskflow.scheduling.AlarmContract

object WidgetKeys {
    val TaskId = ActionParameters.Key<Long>("task_id")
    val Epoch = ActionParameters.Key<Long>("epoch")
    val Delta = ActionParameters.Key<Int>("delta")
}

// Cada ruta lleva un data propio para que Android no reutilice el mismo PendingIntent entre rutas distintas
fun widgetOpenIntent(context: Context, route: String): Intent =
    Intent(context, mainactivity::class.java).apply {
        putExtra(AlarmContract.EXTRA_OPEN_ROUTE, route)
        data = Uri.parse("taskflow://widget/open/" + route)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

// Abre la pantalla de un dia (desde ahi se puede crear evento o tarea con el boton +)
fun widgetDayIntent(context: Context, epochDay: Long, source: String): Intent =
    widgetOpenIntent(context, "day/" + epochDay).apply {
        data = Uri.parse("taskflow://widget/" + source + "/" + epochDay)
    }

// Abre el dialogo de captura rapida con la fecha ya puesta
fun widgetQuickAddIntent(context: Context, epochDay: Long): Intent =
    Intent(context, mainactivity::class.java).apply {
        action = CaptureContract.ACTION_QUICK_ADD
        putExtra(CaptureContract.EXTRA_QUICK_DATE, epochDay)
        data = Uri.parse("taskflow://widget/quickadd/" + epochDay)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

// Misma ruta que la notificacion: escribe en la base de datos y refresca alarmas, notificaciones y widgets
class ToggleWidgetTaskCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.TaskId] ?: return
        val entry = widgetEntryPoint(context)
        val task = entry.taskDao().getTaskById(id) ?: return
        if (!task.isCompleted) entry.completion().complete(task) else entry.completion().uncomplete(task)
        try { entry.widgetUpdater().updateAll() } catch (e: Exception) { }
        try { entry.coordinator().rescheduleAll() } catch (e: Exception) { }
        try { entry.coordinator().refreshNotifications() } catch (e: Exception) { }
    }
}

// delta = 0 vuelve al mes actual
class ShiftMonthCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[WidgetKeys.Delta] ?: 0
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val m = prefs.toMutablePreferences()
            m[WidgetPrefs.MONTH_OFFSET] = if (delta == 0) 0 else (prefs[WidgetPrefs.MONTH_OFFSET] ?: 0) + delta
            m
        }
        MonthWidget().update(context, glanceId)
    }
}

// delta = 0 vuelve a la semana actual
class ShiftWeekCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[WidgetKeys.Delta] ?: 0
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val m = prefs.toMutablePreferences()
            m[WidgetPrefs.WEEK_OFFSET] = if (delta == 0) 0 else (prefs[WidgetPrefs.WEEK_OFFSET] ?: 0) + delta
            m[WidgetPrefs.SELECTED_DAY] = -1L
            m
        }
        WeekWidget().update(context, glanceId)
    }
}

class SelectDayCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val epoch = parameters[WidgetKeys.Epoch] ?: return
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val m = prefs.toMutablePreferences()
            m[WidgetPrefs.SELECTED_DAY] = epoch
            m
        }
        WeekWidget().update(context, glanceId)
    }
}

class RefreshWidgetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val m = prefs.toMutablePreferences()
            m[WidgetPrefs.REFRESH_TICK] = (prefs[WidgetPrefs.REFRESH_TICK] ?: 0) + 1
            m
        }
        try { widgetEntryPoint(context).widgetUpdater().updateAll() } catch (e: Exception) { }
    }
}

// Completa la parte de hoy de un proyecto desde el widget. TaskId lleva el id del nodo; Delta, los minutos.
class CompleteProjectPartCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val nodeId = parameters[WidgetKeys.TaskId] ?: return
        val minutes = parameters[WidgetKeys.Delta] ?: return
        val entry = widgetEntryPoint(context)
        try {
            val today = logicalDateNow(entry.settingsRepo().getDayStartHourSync())
            entry.projectRepository().completeTodayPart(nodeId, minutes, System.currentTimeMillis(), today)
        } catch (e: Exception) { }
        try { entry.widgetUpdater().updateAll() } catch (e: Exception) { }
        try { entry.coordinator().refreshNotifications() } catch (e: Exception) { }
    }
}