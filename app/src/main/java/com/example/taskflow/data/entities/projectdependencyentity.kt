package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// "successorId espera a predecessorId"
@Entity(
    tableName = "project_dependencies",
    primaryKeys = ["predecessorId", "successorId"],
    foreignKeys = [
        ForeignKey(
            entity = ProjectNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["predecessorId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["successorId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("successorId")]
)
data class ProjectDependencyEntity(
    val predecessorId: Long,
    val successorId: Long
)
