package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.taskflow.data.entities.EventExceptionEntity

@Dao
interface EventExceptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: EventExceptionEntity): Long

    @Update
    suspend fun updateException(exception: EventExceptionEntity)

    @Query("DELETE FROM event_exceptions WHERE eventId = :eventId AND originalDate >= :fromDate")
    suspend fun deleteExceptionsFromDate(eventId: Long, fromDate: Long)

    @Query("DELETE FROM event_exceptions WHERE eventId = :eventId AND originalDate = :originalDate")
    suspend fun deleteException(eventId: Long, originalDate: Long)

    @Query("SELECT * FROM event_exceptions WHERE eventId IN (:eventIds)")
    suspend fun getExceptionsForEvents(eventIds: List<Long>): List<EventExceptionEntity>
}
