package com.example.taskflow.ui.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.taskflow.R
import com.example.taskflow.data.entities.RoutineEntity

@Composable
fun weekdayLetters(mask: Int): String {
    val letters = listOf(
        R.string.s2_wd_1, R.string.s2_wd_2, R.string.s2_wd_3, R.string.s2_wd_4,
        R.string.s2_wd_5, R.string.s2_wd_6, R.string.s2_wd_7
    ).map { stringResource(it) }
    return letters.filterIndexed { index, _ -> (mask and (1 shl index)) != 0 }.joinToString(" ")
}

@Composable
fun recurrenceSummary(routine: RoutineEntity): String {
    return if (routine.recurrenceType == "WEEKDAYS") {
        weekdayLetters(routine.weekdaysMask)
    } else {
        stringResource(R.string.s2_every_n_days, routine.intervalDays)
    }
}

fun isValidLogicalWindow(startMin: Int, endMin: Int, dayStartHour: Int): Boolean {
    val offStart = (startMin - (dayStartHour * 60) + 1440) % 1440
    val offEnd = (endMin - (dayStartHour * 60) + 1440) % 1440
    return offEnd > offStart
}
