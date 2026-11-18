package com.example.taskflow.data.daos

import androidx.room.*
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventExceptionEntity
import com.example.taskflow.data.entities.EventReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Query("UPDATE events SET deletedAt = :deletedAt WHERE id = :eventId")
    suspend fun softDeleteEvent(eventId: Long, deletedAt: Long)

    @Insert
    suspend fun insertReminders(reminders: List<EventReminderEntity>)

    @Query("DELETE FROM event_reminders WHERE eventId = :eventId")
    suspend fun deleteRemindersForEvent(eventId: Long)

    @Transaction
    suspend fun insertEventWithReminders(event: EventEntity, reminders: List<EventReminderEntity>) {
        val eventId = insertEvent(event)
        val updatedReminders = reminders.map { it.copy(eventId = eventId) }
        insertReminders(updatedReminders)
    }

    @Transaction
    suspend fun updateEventWithReminders(event: EventEntity, reminders: List<EventReminderEntity>) {
        updateEvent(event)
        deleteRemindersForEvent(event.id)
        val updatedReminders = reminders.map { it.copy(eventId = event.id) }
        insertReminders(updatedReminders)
    }

    @Query("SELECT * FROM events WHERE deletedAt IS NULL AND (recurrenceType != 'NONE' OR date >= :minEpochDay OR (endDate IS NOT NULL AND endDate >= :minEpochDay))")
    fun getActiveEventsFrom(minEpochDay: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deletedAt IS NULL AND (recurrenceType != 'NONE' OR date >= :minEpochDay OR (endDate IS NOT NULL AND endDate >= :minEpochDay))")
    suspend fun getActiveEventsFromSync(minEpochDay: Long): List<EventEntity>

    @Query("SELECT * FROM event_exceptions WHERE eventId IN (:eventIds)")
    fun observeExceptionsForEvents(eventIds: List<Long>): Flow<List<EventExceptionEntity>>

    @Query("SELECT * FROM events WHERE deletedAt IS NULL AND date = :targetDate ORDER BY allDay DESC, startMinuteOfDay ASC")
    fun getEventsForDate(targetDate: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM event_reminders WHERE eventId = :eventId")
    fun getRemindersForEvent(eventId: Long): Flow<List<EventReminderEntity>>

    @Query("SELECT * FROM event_reminders WHERE eventId IN (:eventIds)")
    suspend fun getRemindersForEvents(eventIds: List<Long>): List<EventReminderEntity>

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): EventEntity?

    @Query("SELECT * FROM events WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedEvents(): Flow<List<EventEntity>>

    @Query("DELETE FROM events WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrashEvents()

    @Query("DELETE FROM events WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeDeletedBefore(threshold: Long)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun permanentlyDeleteEvent(eventId: Long)

    @Query("UPDATE events SET deletedAt = NULL WHERE id = :eventId")
    suspend fun restoreEvent(eventId: Long)
}
