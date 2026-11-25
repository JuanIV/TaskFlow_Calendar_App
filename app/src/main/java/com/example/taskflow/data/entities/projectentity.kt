package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val colorArgb: Int,
    val icon: String? = null,
    val startDate: Long,
    val deadlineDate: Long? = null,
    // Capacidad de trabajo diaria en minutos
    val dailyCapacityMinutes: Int,
    // Bit 0 = lunes ... bit 6 = domingo
    val workingDaysMask: Int,
    // ACTIVE, PAUSED, COMPLETED o ARCHIVED
    val status: String,
    val isTemplate: Boolean = false,
    val createdAt: Long,
    val deletedAt: Long? = null,
    // SIMPLE o ADVANCED. null equivale a SIMPLE.
    val kind: String? = null,
    // DYNAMIC, DAILY o NONE (ver domain/trackingmode.kt)
    val trackingMode: String = "DYNAMIC"
)
