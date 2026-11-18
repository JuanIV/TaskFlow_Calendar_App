package com.example.taskflow.domain

data class FocusState(
    val active: Boolean = false,
    // POMODORO o TIMER
    val mode: String = "POMODORO",
    // FOCUS, SHORT_BREAK, LONG_BREAK o TIMER
    val phase: String = "FOCUS",
    val taskId: Long? = null,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val cycle: Int = 0
)
