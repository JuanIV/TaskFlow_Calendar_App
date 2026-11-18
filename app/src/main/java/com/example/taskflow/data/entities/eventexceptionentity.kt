package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("eventId"),
        Index(value = ["eventId", "originalDate"], unique = true)
    ]
)
data class EventExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val originalDate: Long,
    val isDeleted: Boolean,
    val overrideTitle: String,
    val overrideDate: Long,
    val overrideEndDate: Long?,
    val overrideAllDay: Boolean,
    val overrideStartMinute: Int?,
    val overrideEndMinute: Int?,
    val overrideColorArgb: Int,
    val overrideNotes: String?,
    val overrideLocation: String?,
    val overrideImportance: String = "IMPORTANT"
)
