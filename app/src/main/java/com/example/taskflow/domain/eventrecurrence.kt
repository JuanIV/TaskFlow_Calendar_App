package com.example.taskflow.domain

import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventExceptionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class EventOccurrence(
    val eventId: Long,
    val originalDate: Long,
    val startDate: Long,
    val endDate: Long?,
    val allDay: Boolean,
    val startMinute: Int?,
    val endMinute: Int?,
    val title: String,
    val colorArgb: Int,
    val notes: String?,
    val location: String?,
    val isException: Boolean,
    val priority: String = "IMPORTANT",
    val icon: String? = null,
    val external: Boolean = false
)

val EventOccurrence.startMinuteOfDay: Int?
    get() = this.startMinute

val EventOccurrence.endMinuteOfDay: Int?
    get() = this.endMinute

data class ReminderTrigger(val triggerMs: Long, val occurrenceOriginalDate: Long)

fun expandEvents(
    events: List<EventEntity>,
    exceptions: List<EventExceptionEntity>,
    fromEpoch: Long,
    toEpoch: Long
): List<EventOccurrence> {
    val result = mutableListOf<EventOccurrence>()
    val exceptionMap = exceptions.associateBy { Pair(it.eventId, it.originalDate) }

    for (event in events) {
        var iter = 0
        var currentDate = LocalDate.ofEpochDay(event.date)
        var count = 0
        val durationDays = if (event.endDate != null && event.endDate >= event.date) event.endDate - event.date else 0L

        while (iter < 20000) {
            val currentEpoch = currentDate.toEpochDay()

            if (event.recurrenceEndType == "UNTIL" && event.recurrenceUntil != null && currentEpoch > event.recurrenceUntil) break
            if (event.recurrenceEndType == "COUNT" && event.recurrenceCount != null && count >= event.recurrenceCount) break

            val exception = exceptionMap[Pair(event.id, currentEpoch)]
            if (exception != null) {
                if (!exception.isDeleted) {
                    val exDuration = if (exception.overrideEndDate != null && exception.overrideEndDate >= exception.overrideDate) {
                        exception.overrideEndDate - exception.overrideDate
                    } else 0L
                    val exEnd = exception.overrideDate + exDuration
                    if (exception.overrideDate <= toEpoch && exEnd >= fromEpoch) {
                        result.add(
                            EventOccurrence(
                                eventId = event.id,
                                originalDate = currentEpoch,
                                startDate = exception.overrideDate,
                                endDate = exception.overrideEndDate,
                                allDay = exception.overrideAllDay,
                                startMinute = exception.overrideStartMinute,
                                endMinute = exception.overrideEndMinute,
                                title = exception.overrideTitle,
                                colorArgb = exception.overrideColorArgb,
                                notes = exception.overrideNotes,
                                location = exception.overrideLocation,
                                isException = true,
                                priority = exception.overrideImportance,
                                icon = event.icon
                            )
                        )
                    }
                }
            } else {
                val occEnd = currentEpoch + durationDays
                if (currentEpoch <= toEpoch && occEnd >= fromEpoch) {
                    result.add(
                        EventOccurrence(
                            eventId = event.id,
                            originalDate = currentEpoch,
                            startDate = currentEpoch,
                            endDate = if (durationDays > 0) occEnd else null,
                            allDay = event.allDay,
                            startMinute = event.startMinuteOfDay,
                            endMinute = event.endMinuteOfDay,
                            title = event.title,
                            colorArgb = event.colorArgb,
                            notes = event.notes,
                            location = event.location,
                            isException = false,
                            priority = event.priority,
                            icon = event.icon
                        )
                    )
                }
            }

            count++
            if (event.recurrenceType == "NONE") break

            val nextDate = nextOccurrence(currentDate, event, iter + 1)
            if (nextDate.toEpochDay() <= currentEpoch) break

            currentDate = nextDate
            iter++

            if (toEpoch != Long.MAX_VALUE && currentDate.toEpochDay() > toEpoch + 365) break
        }
    }
    return result
}

private fun nextOccurrence(current: LocalDate, event: EventEntity, iteration: Int): LocalDate {
    val interval = event.recurrenceInterval.coerceAtLeast(1).toLong()
    val startDate = LocalDate.ofEpochDay(event.date)

    return when (event.recurrenceType) {
        "DAILY" -> current.plusDays(interval)
        "WEEKLY" -> {
            var nextDay = current.plusDays(1)
            val mask = if (event.recurrenceWeekdaysMask == 0) (1 shl (startDate.dayOfWeek.value - 1)) else event.recurrenceWeekdaysMask
            val startMonday = startDate.with(DayOfWeek.MONDAY)

            while (true) {
                val bitIndex = nextDay.dayOfWeek.value - 1
                if ((mask and (1 shl bitIndex)) != 0) {
                    val currentMonday = nextDay.with(DayOfWeek.MONDAY)
                    val weeksBetween = ChronoUnit.WEEKS.between(startMonday, currentMonday)
                    if (weeksBetween % interval == 0L) break
                }
                nextDay = nextDay.plusDays(1)
            }
            nextDay
        }
        "MONTHLY_DAY" -> {
            val targetMonth = startDate.plusMonths(iteration.toLong() * interval)
            val dayOfMonth = minOf(startDate.dayOfMonth, targetMonth.lengthOfMonth())
            targetMonth.withDayOfMonth(dayOfMonth)
        }
        "MONTHLY_NTH_WEEKDAY" -> {
            val startDayOfWeek = startDate.dayOfWeek
            val nth = (startDate.dayOfMonth - 1) / 7 + 1
            val targetMonth = startDate.plusMonths(iteration.toLong() * interval)
            var targetDate = targetMonth.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(nth, startDayOfWeek))
            if (targetDate.month != targetMonth.month) {
                targetDate = targetMonth.withDayOfMonth(1).with(TemporalAdjusters.lastInMonth(startDayOfWeek))
            }
            targetDate
        }
        "YEARLY" -> {
            val targetYear = startDate.plusYears(iteration.toLong() * interval)
            if (startDate.monthValue == 2 && startDate.dayOfMonth == 29 && !targetYear.isLeapYear) {
                targetYear.withMonth(2).withDayOfMonth(28)
            } else {
                targetYear.withMonth(startDate.monthValue).withDayOfMonth(minOf(startDate.dayOfMonth, targetYear.lengthOfMonth()))
            }
        }
        else -> current.plusDays(1)
    }
}

fun nextReminderOccurrence(
    event: EventEntity,
    exceptions: List<EventExceptionEntity>,
    reminderOffsetMinutes: Int,
    nowMs: Long
): ReminderTrigger? {
    val zone = ZoneId.systemDefault()
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val searchStartEpoch = nowDate.minusDays(30).toEpochDay()
    val searchEndEpoch = nowDate.plusDays(400).toEpochDay()

    val occurrences = expandEvents(listOf(event), exceptions, searchStartEpoch, searchEndEpoch)
    var best: ReminderTrigger? = null

    for (occ in occurrences) {
        val startMin = occ.startMinute
        val occStartMs = if (occ.allDay || startMin == null) {
            LocalDate.ofEpochDay(occ.startDate).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            LocalDate.ofEpochDay(occ.startDate).atTime(startMin / 60, startMin % 60).atZone(zone).toInstant().toEpochMilli()
        }
        val triggerMs = occStartMs - (reminderOffsetMinutes * 60 * 1000L)
        if (triggerMs > nowMs && (best == null || triggerMs < best.triggerMs)) {
            best = ReminderTrigger(triggerMs, occ.originalDate)
        }
    }
    return best
}

fun nextReminderTrigger(
    event: EventEntity,
    exceptions: List<EventExceptionEntity>,
    reminderOffsetMinutes: Int,
    nowMs: Long
): Long? = nextReminderOccurrence(event, exceptions, reminderOffsetMinutes, nowMs)?.triggerMs
