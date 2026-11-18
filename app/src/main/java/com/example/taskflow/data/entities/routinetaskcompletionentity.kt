package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "routine_task_completions",
    primaryKeys = ["routineTaskId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineTaskId")]
)
data class RoutineTaskCompletionEntity(
    val routineTaskId: Long,
    val date: Long,
    val completedAt: Long
)