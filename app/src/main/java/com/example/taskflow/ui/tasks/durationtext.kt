package com.example.taskflow.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.taskflow.R

@Composable
fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.s3_duration_min, m)
        m == 0 -> stringResource(R.string.s3_duration_h, h)
        else -> stringResource(R.string.s3_duration_hm, h, m)
    }
}
