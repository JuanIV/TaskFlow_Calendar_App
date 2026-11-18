package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentTaskId")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val priority: Int = 0,
    val dueMinuteOfDay: Int? = null,
    val notes: String? = null,
    val parentTaskId: Long? = null,
    val originalDate: Long? = null,
    val deletedAt: Long? = null,
    // Fase 3
    val estimatedMinutes: Int? = null,
    // NONE, DAILY, WEEKLY, MONTHLY o YEARLY
    val recurrenceType: String = "NONE",
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdaysMask: Int = 0,
    // true = al completarla ya se creo la siguiente repeticion
    val recurrenceSpawned: Boolean = false
)
