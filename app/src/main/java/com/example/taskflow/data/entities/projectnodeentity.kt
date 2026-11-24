package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_nodes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("parentId")]
)
data class ProjectNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    // null = nodo raiz
    val parentId: Long? = null,
    val title: String,
    val notes: String? = null,
    val sortOrder: Int,
    // Milestone = duracion 0
    val isMilestone: Boolean = false,
    // Duracion estimada en minutos (solo hojas)
    val estimatedMinutes: Int = 0,
    // Minutos ya trabajados en la hoja
    val doneMinutes: Int = 0,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    // Tiempo real con el que se completo (opcional)
    val actualMinutes: Int? = null,
    // 0 = ninguna, 1 = baja, 2 = media, 3 = alta
    val priority: Int = 0,
    val colorArgb: Int? = null,
    // Restriccion "no antes de" (epochDay)
    val notBeforeDate: Long? = null,
    // Dia (epochDay) en el que el usuario la adelanto a hoy
    val pulledForwardDay: Long? = null,
    val createdAt: Long
)
