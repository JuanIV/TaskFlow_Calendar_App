package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val recurrenceType: String,
    val weekdaysMask: Int,
    val intervalDays: Int,
    val anchorDate: Long,
    val startMinuteOfDay: Int,
    val isActive: Boolean = true,
    val createdAt: Long,
    val endMinuteOfDay: Int? = null,
    val endWarningMinutes: Int? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val pausedFromEpochDay: Long? = null,
    val pausedUntilEpochDay: Long? = null,
    val graceDaysPerMonth: Int = 0,
    val isTemplate: Boolean = false,
    val icon: String? = null,
    // true = las tareas se completan en orden (paquete 5)
    val sequentialMode: Boolean = false
)
