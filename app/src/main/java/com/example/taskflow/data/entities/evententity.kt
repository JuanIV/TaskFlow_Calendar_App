package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: Long,
    val allDay: Boolean = true,
    val startMinuteOfDay: Int? = null,
    val colorArgb: Int,
    val createdAt: Long,
    val endDate: Long? = null,
    val endMinuteOfDay: Int? = null,
    val notes: String? = null,
    val location: String? = null,
    val recurrenceType: String = "NONE",
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdaysMask: Int = 0,
    val recurrenceEndType: String = "NEVER",
    val recurrenceUntil: Long? = null,
    val recurrenceCount: Int? = null,
    val deletedAt: Long? = null,
    // Valores: IMPORTANT o SECONDARY
    val priority: String = "IMPORTANT",
    // Clave de icono (ver paquete 5) o null
    val icon: String? = null,
    // true = el recordatorio salta como alarma a pantalla completa (paquete 6)
    val alarmMode: Boolean = false
)
