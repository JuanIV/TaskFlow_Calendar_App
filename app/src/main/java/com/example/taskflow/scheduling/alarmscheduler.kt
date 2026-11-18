package com.example.taskflow.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AlarmScheduler {
    fun schedule(key: String, action: String, extras: Bundle, triggerAtMs: Long)
    fun cancel(key: String, action: String)
}

@Singleton
class AlarmSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(key: String, action: String, extras: Bundle, triggerAtMs: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            this.data = Uri.parse("taskflow://alarm/$key")
            putExtras(extras)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        } catch (e: SecurityException) {
            // Permiso de alarmas exactas denegado
        }
    }

    override fun cancel(key: String, action: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            this.data = Uri.parse("taskflow://alarm/$key")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
