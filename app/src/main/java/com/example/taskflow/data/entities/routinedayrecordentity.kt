package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "routine_day_records",
    primaryKeys = ["routineId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineDayRecordEntity(
    val routineId: Long,
    val date: Long,
    val status: String,
    val requiredCount: Int,
    val doneCount: Int
)
