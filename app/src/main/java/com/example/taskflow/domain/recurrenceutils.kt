package com.example.taskflow.domain

import android.content.Context
import com.example.taskflow.R
import com.example.taskflow.data.entities.EventEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun buildReadableRecurrence(context: Context, event: EventEntity): String {
    if (event.recurrenceType == "NONE") return ""

    val interval = event.recurrenceInterval
    var baseText = ""

    when (event.recurrenceType) {
        "DAILY" -> {
            baseText = if (interval == 1) context.getString(R.string.recurrence_daily_label)
            else context.getString(R.string.recurrence_daily_interval, interval)
        }
        "WEEKLY" -> {
            val daysList = mutableListOf<String>()
            val mask = event.recurrenceWeekdaysMask
            if ((mask and 1) != 0) daysList.add(context.getString(R.string.day_mon))
            if ((mask and 2) != 0) daysList.add(context.getString(R.string.day_tue))
            if ((mask and 4) != 0) daysList.add(context.getString(R.string.day_wed))
            if ((mask and 8) != 0) daysList.add(context.getString(R.string.day_thu))
            if ((mask and 16) != 0) daysList.add(context.getString(R.string.day_fri))
            if ((mask and 32) != 0) daysList.add(context.getString(R.string.day_sat))
            if ((mask and 64) != 0) daysList.add(context.getString(R.string.day_sun))

            val daysStr = daysList.joinToString(", ")

            baseText = if (interval == 1) context.getString(R.string.recurrence_weekly_label, daysStr)
            else context.getString(R.string.recurrence_weekly_interval, interval, daysStr)
        }
        "MONTHLY", "MONTHLY_DAY", "MONTHLY_NTH_WEEKDAY" -> {
            baseText = if (interval == 1) context.getString(R.string.recurrence_monthly)
            else context.getString(R.string.recurrence_monthly_interval, interval)
        }
        "YEARLY" -> {
            baseText = if (interval == 1) context.getString(R.string.recurrence_yearly_label)
            else context.getString(R.string.recurrence_yearly_interval, interval)
        }
    }

    if (event.recurrenceEndType == "UNTIL" && event.recurrenceUntil != null) {
        val dateStr = LocalDate.ofEpochDay(event.recurrenceUntil).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        baseText += context.getString(R.string.recurrence_until, dateStr)
    } else if (event.recurrenceEndType == "COUNT" && event.recurrenceCount != null) {
        baseText += context.getString(R.string.recurrence_count, event.recurrenceCount)
    }

    return baseText
}
