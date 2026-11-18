package com.example.taskflow.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_records")
data class DayRecordEntity(
    @PrimaryKey val date: Long,
    val status: String,
    val requiredCount: Int,
    val doneCount: Int
)
