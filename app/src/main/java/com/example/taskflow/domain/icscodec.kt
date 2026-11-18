package com.example.taskflow.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class IcsEvent(
    val title: String,
    val startDay: Long,
    val allDay: Boolean,
    val startMinute: Int?,
    val endDay: Long?,
    val endMinute: Int?,
    val notes: String?,
    val location: String?,
    val recurrenceType: String,
    val recurrenceInterval: Int,
    val recurrenceMask: Int,
    val recurrenceEndType: String,
    val recurrenceUntil: Long?,
    val recurrenceCount: Int?,
    val reminders: List<Int>,
    val excludedDays: List<Long> = emptyList()
)

private data class IcsMoment(val day: Long, val minute: Int?)

private val DAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
private val DATE_FMT = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT)
private val DATETIME_FMT = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT)
private val STAMP_FMT = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ROOT)
private val TRIGGER_RE = Regex("^-P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

private fun unfold(text: String): List<String> {
    val out = ArrayList<String>()
    for (raw in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")) {
        if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
            out[out.size - 1] = out[out.size - 1] + raw.substring(1)
        } else {
            out.add(raw)
        }
    }
    return out
}

private fun unescapeText(s: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            val n = s[i + 1]
            sb.append(if (n == 'n' || n == 'N') '\n' else n)
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun escapeText(s: String): String =
    s.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")

private fun fold(line: String): String {
    if (line.length <= 73) return line
    val sb = StringBuilder()
    var i = 0
    while (i < line.length) {
        val end = minOf(i + 73, line.length)
        if (i > 0) sb.append("\r\n ")
        sb.append(line, i, end)
        i = end
    }
    return sb.toString()
}

private fun parseMoment(params: String, value: String): IcsMoment? {
    val v = value.trim()
    return try {
        if (v.length == 8) {
            IcsMoment(LocalDate.parse(v, DATE_FMT).toEpochDay(), null)
        } else {
            val utc = v.endsWith("Z")
            val local = LocalDateTime.parse(v.removeSuffix("Z"), DATETIME_FMT)
            val target = ZoneId.systemDefault()
            val tzid = params.split(";")
                .firstOrNull { it.uppercase(Locale.ROOT).startsWith("TZID=") }
                ?.substring(5)?.trim('"')
            val zoned: ZonedDateTime = when {
                utc -> local.atZone(ZoneOffset.UTC).withZoneSameInstant(target)
                tzid != null -> try {
                    local.atZone(ZoneId.of(tzid)).withZoneSameInstant(target)
                } catch (e: Exception) {
                    local.atZone(target)
                }
                else -> local.atZone(target)
            }
            IcsMoment(zoned.toLocalDate().toEpochDay(), zoned.hour * 60 + zoned.minute)
        }
    } catch (e: Exception) {
        null
    }
}

private fun parseTrigger(value: String): Int? {
    val m = TRIGGER_RE.find(value.trim().uppercase(Locale.ROOT)) ?: return null
    if (m.groupValues.drop(1).all { it.isEmpty() }) return null
    val d = m.groupValues[1].toIntOrNull() ?: 0
    val h = m.groupValues[2].toIntOrNull() ?: 0
    val min = m.groupValues[3].toIntOrNull() ?: 0
    return d * 1440 + h * 60 + min
}

fun parseIcs(text: String): List<IcsEvent> {
    val result = ArrayList<IcsEvent>()
    var inEvent = false
    var inAlarm = false
    var summary = ""
    var description: String? = null
    var location: String? = null
    var start: IcsMoment? = null
    var end: IcsMoment? = null
    var rrule: String? = null
    var alarms = ArrayList<Int>()

    for (line in unfold(text)) {
        val idx = line.indexOf(':')
        if (idx <= 0) continue
        val head = line.substring(0, idx)
        val value = line.substring(idx + 1)
        val name = head.substringBefore(';').trim().uppercase(Locale.ROOT)
        val params = head.substringAfter(';', "")

        if (name == "BEGIN") {
            val kind = value.trim().uppercase(Locale.ROOT)
            if (kind == "VEVENT") {
                inEvent = true
                inAlarm = false
                summary = ""
                description = null
                location = null
                start = null
                end = null
                rrule = null
                alarms = ArrayList()
            } else if (kind == "VALARM" && inEvent) {
                inAlarm = true
            }
            continue
        }
        if (name == "END") {
            val kind = value.trim().uppercase(Locale.ROOT)
            if (kind == "VALARM") {
                inAlarm = false
            } else if (kind == "VEVENT" && inEvent) {
                inEvent = false
                val s = start
                if (s != null) {
                    val e = end
                    val allDay = s.minute == null
                    var endDay: Long? = null
                    var endMinute: Int? = null
                    if (e != null) {
                        if (allDay) {
                            val lastDay = e.day - 1
                            if (lastDay > s.day) endDay = lastDay
                        } else if (e.minute != null) {
                            if (e.day > s.day) endDay = e.day
                            endMinute = e.minute
                            if (e.day == s.day && e.minute <= (s.minute ?: 0)) endMinute = null
                        }
                    }

                    var recType = "NONE"
                    var interval = 1
                    var mask = 0
                    var endType = "NEVER"
                    var until: Long? = null
                    var count: Int? = null
                    val rule = rrule
                    if (rule != null) {
                        val parts = rule.split(";").mapNotNull { p ->
                            val i = p.indexOf('=')
                            if (i > 0) Pair(p.substring(0, i).uppercase(Locale.ROOT), p.substring(i + 1)) else null
                        }.toMap()
                        interval = (parts["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                        val byDay = (parts["BYDAY"] ?: "").split(",")
                            .map { it.trim().uppercase(Locale.ROOT) }
                            .filter { it.isNotEmpty() }
                        recType = when (parts["FREQ"]?.uppercase(Locale.ROOT)) {
                            "DAILY" -> "DAILY"
                            "WEEKLY" -> {
                                for (token in byDay) {
                                    val bit = DAY_CODES.indexOf(token.takeLast(2))
                                    if (bit >= 0) mask = mask or (1 shl bit)
                                }
                                "WEEKLY"
                            }
                            "MONTHLY" -> if (byDay.any { it.length > 2 }) "MONTHLY_NTH_WEEKDAY" else "MONTHLY_DAY"
                            "YEARLY" -> "YEARLY"
                            else -> "NONE"
                        }
                        if (recType != "NONE") {
                            val c = parts["COUNT"]?.toIntOrNull()
                            val u = parts["UNTIL"]?.let { parseMoment("", it) }
                            if (c != null && c >= 1) {
                                endType = "COUNT"
                                count = c
                            } else if (u != null) {
                                endType = "UNTIL"
                                until = u.day
                            }
                        } else {
                            interval = 1
                            mask = 0
                        }
                    }

                    result.add(
                        IcsEvent(
                            title = summary.ifBlank { "Sin título" },
                            startDay = s.day,
                            allDay = allDay,
                            startMinute = s.minute,
                            endDay = endDay,
                            endMinute = endMinute,
                            notes = description?.takeIf { it.isNotBlank() },
                            location = location?.takeIf { it.isNotBlank() },
                            recurrenceType = recType,
                            recurrenceInterval = interval,
                            recurrenceMask = mask,
                            recurrenceEndType = endType,
                            recurrenceUntil = until,
                            recurrenceCount = count,
                            reminders = if (allDay) emptyList() else alarms.distinct()
                        )
                    )
                }
            }
            continue
        }
        if (!inEvent) continue
        if (inAlarm) {
            if (name == "TRIGGER") parseTrigger(value)?.let { alarms.add(it) }
            continue
        }
        when (name) {
            "SUMMARY" -> summary = unescapeText(value).trim()
            "DESCRIPTION" -> description = unescapeText(value).trim()
            "LOCATION" -> location = unescapeText(value).trim()
            "DTSTART" -> start = parseMoment(params, value)
            "DTEND" -> end = parseMoment(params, value)
            "RRULE" -> rrule = value.trim()
        }
    }
    return result
}

private fun fmtDate(day: Long): String = LocalDate.ofEpochDay(day).format(DATE_FMT)

private fun fmtTime(day: Long, minute: Int): String =
    LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60).format(DATETIME_FMT)

fun buildIcs(events: List<IcsEvent>): String {
    val sb = StringBuilder()
    fun add(line: String) {
        sb.append(fold(line)).append("\r\n")
    }
    val stamp = ZonedDateTime.now(ZoneOffset.UTC).format(STAMP_FMT)
    add("BEGIN:VCALENDAR")
    add("VERSION:2.0")
    add("PRODID:-//TaskFlow//ES")
    add("CALSCALE:GREGORIAN")
    events.forEachIndexed { index, ev ->
        add("BEGIN:VEVENT")
        add("UID:taskflow-" + index + "-" + ev.startDay + "@taskflow")
        add("DTSTAMP:" + stamp)
        if (ev.allDay) {
            add("DTSTART;VALUE=DATE:" + fmtDate(ev.startDay))
            add("DTEND;VALUE=DATE:" + fmtDate((ev.endDay ?: ev.startDay) + 1))
        } else {
            add("DTSTART:" + fmtTime(ev.startDay, ev.startMinute ?: 0))
            if (ev.endMinute != null || ev.endDay != null) {
                add("DTEND:" + fmtTime(ev.endDay ?: ev.startDay, ev.endMinute ?: (ev.startMinute ?: 0)))
            }
        }
        add("SUMMARY:" + escapeText(ev.title))
        if (ev.notes != null) add("DESCRIPTION:" + escapeText(ev.notes))
        if (ev.location != null) add("LOCATION:" + escapeText(ev.location))
        if (ev.recurrenceType != "NONE") {
            val rule = StringBuilder()
            when (ev.recurrenceType) {
                "DAILY" -> rule.append("FREQ=DAILY")
                "WEEKLY" -> {
                    rule.append("FREQ=WEEKLY")
                    val codes = (0..6).filter { (ev.recurrenceMask and (1 shl it)) != 0 }.map { DAY_CODES[it] }
                    if (codes.isNotEmpty()) rule.append(";BYDAY=").append(codes.joinToString(","))
                }
                "MONTHLY_NTH_WEEKDAY" -> {
                    val d = LocalDate.ofEpochDay(ev.startDay)
                    val nth = (d.dayOfMonth - 1) / 7 + 1
                    rule.append("FREQ=MONTHLY;BYDAY=").append(nth).append(DAY_CODES[d.dayOfWeek.value - 1])
                }
                "YEARLY" -> rule.append("FREQ=YEARLY")
                else -> rule.append("FREQ=MONTHLY")
            }
            if (ev.recurrenceInterval > 1) rule.append(";INTERVAL=").append(ev.recurrenceInterval)
            val count = ev.recurrenceCount
            val until = ev.recurrenceUntil
            if (ev.recurrenceEndType == "COUNT" && count != null) {
                rule.append(";COUNT=").append(count)
            } else if (ev.recurrenceEndType == "UNTIL" && until != null) {
                rule.append(";UNTIL=").append(if (ev.allDay) fmtDate(until) else fmtTime(until, 23 * 60 + 59))
            }
            add("RRULE:" + rule.toString())
            if (ev.excludedDays.isNotEmpty()) {
                if (ev.allDay) {
                    add("EXDATE;VALUE=DATE:" + ev.excludedDays.joinToString(",") { fmtDate(it) })
                } else {
                    add("EXDATE:" + ev.excludedDays.joinToString(",") { fmtTime(it, ev.startMinute ?: 0) })
                }
            }
        }
        if (!ev.allDay) {
            ev.reminders.filter { it >= 0 }.forEach { minutes ->
                add("BEGIN:VALARM")
                add("TRIGGER:-PT" + minutes + "M")
                add("ACTION:DISPLAY")
                add("DESCRIPTION:Recordatorio")
                add("END:VALARM")
            }
        }
        add("END:VEVENT")
    }
    add("END:VCALENDAR")
    return sb.toString()
}
