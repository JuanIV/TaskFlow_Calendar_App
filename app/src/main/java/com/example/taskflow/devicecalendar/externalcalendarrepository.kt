package com.example.taskflow.devicecalendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.EventOccurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceCalendar(val id: Long, val name: String, val account: String, val visible: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ExternalCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun listCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val result = ArrayList<DeviceCalendar>()
        if (hasPermission()) {
            try {
                val projection = arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.VISIBLE
                )
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    null,
                    null,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        result.add(DeviceCalendar(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3) != 0))
                    }
                }
            } catch (e: Exception) {
                // sin acceso al proveedor de calendarios
            }
        }
        result
    }

    fun observe(fromEpoch: Long, toEpoch: Long): Flow<List<EventOccurrence>> =
        combine(settingsRepo.extCalendarEnabledFlow, settingsRepo.extCalendarIdsFlow) { enabled, ids -> Pair(enabled, ids) }
            .flatMapLatest { (enabled, ids) ->
                if (!enabled || ids.isEmpty() || !hasPermission()) {
                    flowOf(emptyList<EventOccurrence>())
                } else {
                    calendarChanges().map { withContext(Dispatchers.IO) { readInstances(fromEpoch, toEpoch, ids) } }
                }
            }
            .distinctUntilChanged()

    private fun calendarChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun readInstances(fromEpoch: Long, toEpoch: Long, ids: Set<Long>): List<EventOccurrence> {
        val lastEpoch = minOf(toEpoch, fromEpoch + 800)
        val beginMs = LocalDate.ofEpochDay(fromEpoch - 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endMs = LocalDate.ofEpochDay(lastEpoch + 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, beginMs)
        ContentUris.appendId(builder, endMs)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.CALENDAR_ID
        )
        val zone = ZoneId.systemDefault()
        val out = ArrayList<EventOccurrence>()
        try {
            context.contentResolver.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getLong(8) !in ids) continue
                    val eventId = c.getLong(0)
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(sin título)"
                    val beginInstant = Instant.ofEpochMilli(c.getLong(2))
                    val endInstant = Instant.ofEpochMilli(c.getLong(3))
                    val allDay = c.getInt(4) != 0
                    val location = c.getString(5)?.takeIf { it.isNotBlank() }
                    val notes = c.getString(6)?.takeIf { it.isNotBlank() }
                    val color = c.getInt(7).let { if (it == 0) 0xFF1E88E5.toInt() else it }

                    val startDay: Long
                    val endDay: Long?
                    val startMinute: Int?
                    val endMinute: Int?
                    if (allDay) {
                        startDay = beginInstant.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        val lastDay = endInstant.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() - 1
                        endDay = if (lastDay > startDay) lastDay else null
                        startMinute = null
                        endMinute = null
                    } else {
                        val s = beginInstant.atZone(zone)
                        val e = endInstant.atZone(zone)
                        startDay = s.toLocalDate().toEpochDay()
                        startMinute = s.hour * 60 + s.minute
                        var ed = e.toLocalDate().toEpochDay()
                        var em = e.hour * 60 + e.minute
                        if (ed > startDay && em == 0) {
                            ed -= 1
                            em = 1440
                        }
                        endDay = if (ed > startDay) ed else null
                        endMinute = em
                    }

                    if (startDay > toEpoch || (endDay ?: startDay) < fromEpoch) continue
                    out.add(
                        EventOccurrence(
                            eventId = -eventId,
                            originalDate = startDay,
                            startDate = startDay,
                            endDate = endDay,
                            allDay = allDay,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            title = title,
                            colorArgb = color,
                            notes = notes,
                            location = location,
                            isException = false,
                            priority = "IMPORTANT",
                            icon = null,
                            external = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // sin acceso al proveedor de calendarios
        }
        return out
    }
}
