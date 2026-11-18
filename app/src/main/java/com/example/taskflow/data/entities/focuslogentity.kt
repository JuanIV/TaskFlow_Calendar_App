package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_logs", indices = [Index("taskId")])
data class FocusLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,
    val startedAt: Long,
    val durationMinutes: Int,
    // FOCUS, SHORT_BREAK, LONG_BREAK o TIMER
    val type: String
)
