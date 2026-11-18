package com.example.taskflow.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.backup.SyncManager
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.EventExceptionDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventReminderEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.EventColors
import com.example.taskflow.domain.IcsEvent
import com.example.taskflow.domain.buildCsv
import com.example.taskflow.domain.buildIcs
import com.example.taskflow.domain.parseIcs
import com.example.taskflow.scheduling.SchedulingCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventDao: EventDao,
    private val exceptionDao: EventExceptionDao,
    private val taskDao: TaskDao,
    private val settingsRepo: SettingsRepository,
    private val coordinator: SchedulingCoordinator,
    private val syncManager: SyncManager
) : ViewModel() {

    val syncFolder: StateFlow<String> = settingsRepo.syncFolderFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private fun clock(minute: Int?): String =
        if (minute == null) "" else String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

    private fun dayText(day: Long?): String =
        if (day == null) "" else LocalDate.ofEpochDay(day).toString()

    private fun writeText(uri: Uri, onResult: (Boolean) -> Unit, producer: suspend () -> String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val text = producer()
                    val out = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            onResult(ok)
        }
    }

    fun exportIcs(uri: Uri, onResult: (Boolean) -> Unit) = writeText(uri, onResult) {
        val events = eventDao.getActiveEventsFromSync(0L)
        val ids = events.map { it.id }
        val exceptions = if (ids.isEmpty()) emptyList() else exceptionDao.getExceptionsForEvents(ids)
        val reminders = if (ids.isEmpty()) emptyList() else eventDao.getRemindersForEvents(ids)
        buildIcs(
            events.map { e ->
                IcsEvent(
                    title = e.title,
                    startDay = e.date,
                    allDay = e.allDay,
                    startMinute = e.startMinuteOfDay,
                    endDay = e.endDate?.takeIf { it > e.date },
                    endMinute = e.endMinuteOfDay,
                    notes = e.notes,
                    location = e.location,
                    recurrenceType = e.recurrenceType,
                    recurrenceInterval = e.recurrenceInterval,
                    recurrenceMask = e.recurrenceWeekdaysMask,
                    recurrenceEndType = e.recurrenceEndType,
                    recurrenceUntil = e.recurrenceUntil,
                    recurrenceCount = e.recurrenceCount,
                    reminders = reminders.filter { it.eventId == e.id }.map { it.offsetMinutes },
                    excludedDays = exceptions.filter { it.eventId == e.id && it.isDeleted }.map { it.originalDate }
                )
            }
        )
    }

    fun exportTasksCsv(uri: Uri, onResult: (Boolean) -> Unit) = writeText(uri, onResult) {
        val tasks = taskDao.getAllActiveTasksSync()
        val titles = tasks.associate { it.id to it.title }
        buildCsv(
            listOf("titulo", "fecha", "hora", "completada", "prioridad", "duracion_estimada_min", "repeticion", "notas", "subtarea_de"),
            tasks.map { t ->
                listOf(
                    t.title,
                    dayText(t.date),
                    clock(t.dueMinuteOfDay),
                    if (t.isCompleted) "SI" else "NO",
                    t.priority.toString(),
                    t.estimatedMinutes?.toString() ?: "",
                    t.recurrenceType,
                    t.notes ?: "",
                    t.parentTaskId?.let { titles[it] } ?: ""
                )
            }
        )
    }

    fun exportEventsCsv(uri: Uri, onResult: (Boolean) -> Unit) = writeText(uri, onResult) {
        val events = eventDao.getActiveEventsFromSync(0L)
        buildCsv(
            listOf("titulo", "fecha_inicio", "fecha_fin", "todo_el_dia", "hora_inicio", "hora_fin", "ubicacion", "notas", "repeticion", "importancia"),
            events.map { e ->
                listOf(
                    e.title,
                    dayText(e.date),
                    dayText(e.endDate),
                    if (e.allDay) "SI" else "NO",
                    clock(e.startMinuteOfDay),
                    clock(e.endMinuteOfDay),
                    e.location ?: "",
                    e.notes ?: "",
                    e.recurrenceType,
                    e.priority
                )
            }
        )
    }

    // Devuelve el numero de eventos nuevos, o -1 si el archivo no se pudo leer
    fun importIcs(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: return@withContext -1
                    val parsed = parseIcs(text)
                    val known = eventDao.getActiveEventsFromSync(0L)
                        .map { it.title + "|" + it.date + "|" + (it.startMinuteOfDay ?: -1) }
                        .toMutableSet()
                    var added = 0
                    val now = System.currentTimeMillis()
                    for (p in parsed) {
                        val key = p.title + "|" + p.startDay + "|" + (p.startMinute ?: -1)
                        if (!known.add(key)) continue
                        eventDao.insertEventWithReminders(
                            EventEntity(
                                title = p.title,
                                date = p.startDay,
                                allDay = p.allDay,
                                startMinuteOfDay = p.startMinute,
                                colorArgb = EventColors[5].toArgb(),
                                createdAt = now,
                                endDate = p.endDay,
                                endMinuteOfDay = p.endMinute,
                                notes = p.notes,
                                location = p.location,
                                recurrenceType = p.recurrenceType,
                                recurrenceInterval = p.recurrenceInterval,
                                recurrenceWeekdaysMask = p.recurrenceMask,
                                recurrenceEndType = p.recurrenceEndType,
                                recurrenceUntil = p.recurrenceUntil,
                                recurrenceCount = p.recurrenceCount
                            ),
                            p.reminders.map { EventReminderEntity(eventId = 0L, offsetMinutes = it) }
                        )
                        added++
                    }
                    if (added > 0) coordinator.onDataChanged()
                    added
                } catch (e: Exception) {
                    -1
                }
            }
            onResult(count)
        }
    }

    fun setFolder(uri: String) {
        viewModelScope.launch { settingsRepo.setSyncFolder(uri) }
    }

    fun syncUp(onResult: (Boolean) -> Unit) {
        val folder = syncFolder.value
        if (folder.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch { onResult(syncManager.upload(folder)) }
    }

    fun syncDown(onResult: (Boolean) -> Unit) {
        val folder = syncFolder.value
        if (folder.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch { onResult(syncManager.download(folder)) }
    }
}
