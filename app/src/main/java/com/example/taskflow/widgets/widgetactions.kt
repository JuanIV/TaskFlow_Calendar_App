package com.example.taskflow.widgets

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.taskflow.mainactivity
import com.example.taskflow.scheduling.AlarmContract

object WidgetKeys {
    val TaskId = ActionParameters.Key<Long>("task_id")
}

fun widgetOpenIntent(context: Context, route: String): Intent =
    Intent(context, mainactivity::class.java).apply {
        putExtra(AlarmContract.EXTRA_OPEN_ROUTE, route)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

// Misma ruta que la notificacion: escribe en la base de datos y refresca alarmas, notificaciones y widgets
class ToggleWidgetTaskCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.TaskId] ?: return
        val entry = widgetEntryPoint(context)
        val task = entry.taskDao().getTaskById(id) ?: return
        val completed = !task.isCompleted
        if (completed) entry.completion().complete(task) else entry.completion().uncomplete(task)
        entry.coordinator().rescheduleAll()
        entry.coordinator().refreshNotifications()
    }
}
