package com.example.taskflow.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.taskflow.R

fun reminderPresets(allDay: Boolean): List<Int> =
    if (allDay) listOf(-540, 240, 900) else listOf(0, 5, 10, 15, 30, 60, 120, 1440)

@Composable
fun reminderLabel(offset: Int, allDay: Boolean): String {
    return if (allDay) {
        when (offset) {
            -540 -> stringResource(R.string.s2_rem_same_day_9)
            240 -> stringResource(R.string.s2_rem_prev_day_20)
            900 -> stringResource(R.string.s2_rem_2days_9)
            else -> stringResource(R.string.s2_rem_min, offset)
        }
    } else {
        when (offset) {
            0 -> stringResource(R.string.s2_rem_at_start)
            60 -> stringResource(R.string.s2_rem_hour)
            120 -> stringResource(R.string.s2_rem_2hours)
            1440 -> stringResource(R.string.s2_rem_1day)
            else -> stringResource(R.string.s2_rem_min, offset)
        }
    }
}
