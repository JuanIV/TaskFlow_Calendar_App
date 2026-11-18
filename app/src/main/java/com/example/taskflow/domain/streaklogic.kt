package com.example.taskflow.domain

import com.example.taskflow.data.entities.DayRecordEntity
import com.example.taskflow.data.entities.RoutineDayRecordEntity

data class DayStatus(val date: Long, val status: String)

fun dayRecordsToStatuses(records: List<DayRecordEntity>): List<DayStatus> =
    records.map { DayStatus(it.date, it.status) }

fun routineRecordsToStatuses(records: List<RoutineDayRecordEntity>): List<DayStatus> =
    records.map { DayStatus(it.date, it.status) }

// todayState: COMPLETED, PENDING o NEUTRAL.
// COMPLETED suma 1, PENDING no rompe ni suma, NEUTRAL no suma.
fun currentStreak(records: List<DayStatus>, todayState: String): Int {
    var streak = 0
    val todayBonus = if (todayState == "COMPLETED") 1 else 0
    for (record in records.sortedByDescending { it.date }) {
        when (record.status) {
            "COMPLETED" -> streak++
            "FAILED" -> return streak + todayBonus
            else -> { }
        }
    }
    return streak + todayBonus
}

fun bestStreak(records: List<DayStatus>, todayState: String): Int {
    var best = 0
    var current = 0
    for (record in records.sortedBy { it.date }) {
        when (record.status) {
            "COMPLETED" -> {
                current++
                if (current > best) best = current
            }
            "FAILED" -> current = 0
            else -> { }
        }
    }
    return maxOf(best, currentStreak(records, todayState))
}

// Porcentaje de cumplimiento: COMPLETED / (COMPLETED + FAILED + GRACE). PAUSED y NEUTRAL se excluyen.
fun compliancePercent(records: List<DayStatus>, fromEpoch: Long, toEpoch: Long): Float {
    val valid = records.filter { it.date in fromEpoch..toEpoch && (it.status == "COMPLETED" || it.status == "FAILED" || it.status == "GRACE") }
    if (valid.isEmpty()) return 0f
    return valid.count { it.status == "COMPLETED" } * 100f / valid.size
}
