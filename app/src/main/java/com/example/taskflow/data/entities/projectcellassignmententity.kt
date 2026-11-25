package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Un dia en el que se trabaja en una hoja de un proyecto avanzado (asignacion manual)
@Entity(
    tableName = "project_cell_assignments",
    primaryKeys = ["nodeId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = ProjectNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("epochDay")]
)
data class ProjectCellAssignmentEntity(
    val nodeId: Long,
    val epochDay: Long
)
