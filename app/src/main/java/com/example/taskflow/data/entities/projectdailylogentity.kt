package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Minutos planificados y trabajados de una hoja en un dia concreto
@Entity(
    tableName = "project_daily_log",
    primaryKeys = ["nodeId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = ProjectNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("nodeId"), Index("epochDay")]
)
data class ProjectDailyLogEntity(
    val nodeId: Long,
    val epochDay: Long,
    val plannedMinutes: Int,
    val doneMinutes: Int
)
