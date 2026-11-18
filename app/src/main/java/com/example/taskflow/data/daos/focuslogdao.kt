package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.taskflow.data.entities.FocusLogEntity

@Dao
interface FocusLogDao {
    @Insert
    suspend fun insert(log: FocusLogEntity): Long

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_logs WHERE taskId = :taskId AND type IN ('FOCUS', 'TIMER')")
    suspend fun totalMinutesForTask(taskId: Long): Int

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_logs WHERE type IN ('FOCUS', 'TIMER')")
    suspend fun totalFocusMinutes(): Int

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_logs WHERE type IN ('FOCUS', 'TIMER') AND startedAt >= :fromMs AND startedAt < :toMs")
    suspend fun focusMinutesBetween(fromMs: Long, toMs: Long): Int

    @Query("SELECT * FROM focus_logs ORDER BY startedAt ASC")
    suspend fun getAllSync(): List<FocusLogEntity>
}
