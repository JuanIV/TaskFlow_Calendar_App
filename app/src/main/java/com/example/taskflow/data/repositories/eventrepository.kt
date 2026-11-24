package com.example.taskflow.data.repositories

import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.EventExceptionDao
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventExceptionEntity
import com.example.taskflow.data.entities.EventReminderEntity
import com.example.taskflow.domain.EventOccurrence
import com.example.taskflow.domain.expandEvents
import com.example.taskflow.domain.planProject
import com.example.taskflow.domain.projectCalendarItems
import com.example.taskflow.devicecalendar.ExternalCalendarRepository
import com.example.taskflow.data.repositories.ProjectRepository
import kotlinx.coroutines.flow.combine
import com.example.taskflow.scheduling.SchedulingCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val exceptionDao: EventExceptionDao,
    private val externalRepo: ExternalCalendarRepository,
    private val coordinator: SchedulingCoordinator,
    private val projectRepository: ProjectRepository
) {
    fun getExpandedEvents(fromEpoch: Long, toEpoch: Long): Flow<List<EventOccurrence>> =
        combine(
            getOwnExpandedEvents(fromEpoch, toEpoch),
            externalRepo.observe(fromEpoch, toEpoch),
            projectOccurrences(fromEpoch, toEpoch)
        ) { own, external, projectItems -> own + external + projectItems }

    // Deadlines e hitos de proyectos como EventOccurrence de solo lectura (eventId negativo con IDs estables)
    // Limitacion: se asume que un proyecto tiene menos de 1000 hitos (index < 1000).
    private fun projectOccurrences(fromEpoch: Long, toEpoch: Long): Flow<List<EventOccurrence>> =
        combine(
            projectRepository.observeActiveProjects(),
            projectRepository.observeAllNodes(),
            projectRepository.observeAllDependencies(),
            projectRepository.observeAllExcludedDays()
        ) { projects, allNodes, allDeps, allExcluded ->
            val list = ArrayList<EventOccurrence>()
            val nodesByProject = allNodes.groupBy { it.projectId }
            val depsByProject = allDeps.groupBy { dep -> allNodes.firstOrNull { it.id == dep.successorId }?.projectId ?: -1L }
            val excludedByProject = allExcluded.groupBy { it.projectId }.mapValues { entry -> entry.value.map { it.epochDay }.toSet() }

            for (project in projects) {
                val pNodes = nodesByProject[project.id].orEmpty()
                val pDeps = depsByProject[project.id].orEmpty()
                val excluded = excludedByProject[project.id].orEmpty()
                val planNodes = buildPlanNodes(pNodes, pDeps)
                val config = project.toPlanConfig(excluded)
                val plan = planProject(config, planNodes, fromEpoch)

                val items = projectCalendarItems(
                    project.name,
                    project.deadlineDate,
                    pNodes.filter { it.isMilestone }.mapNotNull { m -> plan.nodes[m.id]?.startDay?.let { Pair(m.title, it) } }
                )

                items.forEachIndexed { index, item ->
                    if (item.day in fromEpoch..toEpoch) {
                        val stableId = -(project.id * 1000L + index + 1L)
                        list.add(
                            EventOccurrence(
                                eventId = stableId,
                                originalDate = item.day,
                                startDate = item.day,
                                endDate = null,
                                allDay = true,
                                startMinute = null,
                                endMinute = null,
                                title = item.title,
                                colorArgb = item.colorArgb,
                                notes = null,
                                location = null,
                                isException = false,
                                priority = "IMPORTANT",
                                icon = null,
                                external = true
                            )
                        )
                    }
                }
            }
            list
        }
    private fun getOwnExpandedEvents(fromEpoch: Long, toEpoch: Long): Flow<List<EventOccurrence>> {
        val searchFrom = fromEpoch - 365
        return eventDao.getActiveEventsFrom(searchFrom).flatMapLatest { events ->
            val eventIds = events.map { it.id }
            if (eventIds.isEmpty()) return@flatMapLatest flowOf(emptyList())
            eventDao.observeExceptionsForEvents(eventIds).map { exceptions ->
                expandEvents(events, exceptions, fromEpoch, toEpoch)
            }
        }
    }

    suspend fun getEventById(id: Long) = eventDao.getEventById(id)
    fun getEventsForDate(targetDate: Long): Flow<List<EventEntity>> = eventDao.getEventsForDate(targetDate)
    fun getRemindersForEvent(eventId: Long) = eventDao.getRemindersForEvent(eventId)
    suspend fun getRemindersSync(eventId: Long): List<EventReminderEntity> = eventDao.getRemindersForEvents(listOf(eventId))
    fun getDeletedEvents(): Flow<List<EventEntity>> = eventDao.getDeletedEvents()

    suspend fun saveEvent(event: EventEntity, reminders: List<EventReminderEntity>) {
        if (event.id == 0L) {
            eventDao.insertEventWithReminders(event, reminders)
        } else {
            eventDao.updateEventWithReminders(event, reminders)
        }
        coordinator.onDataChanged()
    }

    suspend fun softDeleteEvent(eventId: Long) {
        eventDao.softDeleteEvent(eventId, System.currentTimeMillis())
        coordinator.onDataChanged()
    }

    suspend fun restoreEvent(eventId: Long) {
        eventDao.restoreEvent(eventId)
        coordinator.onDataChanged()
    }

    suspend fun permanentlyDeleteEvent(eventId: Long) {
        eventDao.permanentlyDeleteEvent(eventId)
        coordinator.onDataChanged()
    }

    suspend fun emptyTrashEvents() {
        eventDao.emptyTrashEvents()
        coordinator.onDataChanged()
    }

    suspend fun restoreEventRow(event: EventEntity) {
        eventDao.updateEvent(event)
        coordinator.onDataChanged()
    }

    suspend fun saveException(exception: EventExceptionEntity) {
        exceptionDao.insertException(exception)
        coordinator.onDataChanged()
    }

    suspend fun deleteOccurrence(eventId: Long, originalDate: Long) {
        exceptionDao.insertException(
            EventExceptionEntity(
                eventId = eventId,
                originalDate = originalDate,
                isDeleted = true,
                overrideTitle = "",
                overrideDate = originalDate,
                overrideEndDate = null,
                overrideAllDay = true,
                overrideStartMinute = null,
                overrideEndMinute = null,
                overrideColorArgb = 0,
                overrideNotes = null,
                overrideLocation = null
            )
        )
        coordinator.onDataChanged()
    }

    suspend fun undeleteOccurrence(eventId: Long, originalDate: Long) {
        exceptionDao.deleteException(eventId, originalDate)
        coordinator.onDataChanged()
    }

    suspend fun truncateSeries(eventId: Long, untilEpoch: Long) {
        val originalEvent = eventDao.getEventById(eventId) ?: return
        eventDao.updateEvent(originalEvent.copy(recurrenceEndType = "UNTIL", recurrenceUntil = untilEpoch))
        exceptionDao.deleteExceptionsFromDate(eventId, untilEpoch + 1)
        coordinator.onDataChanged()
    }

    suspend fun splitEventSeries(
        originalEvent: EventEntity,
        splitEpochDate: Long,
        newEvent: EventEntity,
        newReminders: List<EventReminderEntity>
    ) {
        var created = newEvent.copy(id = 0L)
        val oc = originalEvent.recurrenceCount
        val cc = created.recurrenceCount
        if (originalEvent.recurrenceEndType == "COUNT" && created.recurrenceEndType == "COUNT" && oc != null && cc != null && oc == cc) {
            val before = expandEvents(listOf(originalEvent), emptyList(), originalEvent.date, splitEpochDate - 1).size
            created = created.copy(recurrenceCount = maxOf(1, oc - before))
        }
        eventDao.updateEvent(originalEvent.copy(recurrenceEndType = "UNTIL", recurrenceUntil = splitEpochDate - 1))
        exceptionDao.deleteExceptionsFromDate(originalEvent.id, splitEpochDate)
        val eventId = eventDao.insertEvent(created)
        eventDao.insertReminders(newReminders.map { it.copy(id = 0L, eventId = eventId) })
        coordinator.onDataChanged()
    }

    // referenceDate = fecha de inicio actual de la ocurrencia que se esta editando
    suspend fun updateEventSeriesFromOccurrence(
        baseEvent: EventEntity,
        referenceDate: Long,
        updated: EventEntity,
        newReminders: List<EventReminderEntity>
    ) {
        val delta = updated.date - referenceDate
        val updatedSeries = baseEvent.copy(
            title = updated.title,
            date = baseEvent.date + delta,
            endDate = baseEvent.endDate?.let { it + delta } ?: updated.endDate?.let { it + 0L }?.let { baseEvent.date + delta + (it - updated.date) },
            allDay = updated.allDay,
            startMinuteOfDay = updated.startMinuteOfDay,
            endMinuteOfDay = updated.endMinuteOfDay,
            colorArgb = updated.colorArgb,
            notes = updated.notes,
            location = updated.location,
            priority = updated.priority,
            icon = updated.icon,
            alarmMode = updated.alarmMode,
            recurrenceType = updated.recurrenceType,
            recurrenceInterval = updated.recurrenceInterval,
            recurrenceWeekdaysMask = updated.recurrenceWeekdaysMask,
            recurrenceEndType = updated.recurrenceEndType,
            recurrenceUntil = updated.recurrenceUntil,
            recurrenceCount = updated.recurrenceCount
        )
        eventDao.updateEventWithReminders(updatedSeries, newReminders)
        coordinator.onDataChanged()
    }
}
