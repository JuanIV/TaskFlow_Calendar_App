package com.example.taskflow.focus

data class PomodoroConfig(val focusMin: Int, val shortMin: Int, val longMin: Int, val cycles: Int)

data class NextPhase(val phase: String, val cycle: Int)

fun phaseMinutes(phase: String, config: PomodoroConfig): Int = when (phase) {
    "SHORT_BREAK" -> config.shortMin
    "LONG_BREAK" -> config.longMin
    else -> config.focusMin
}

// cycle = numero de fases de enfoque completadas en la tanda actual
fun nextPomodoroPhase(current: String, cycle: Int, config: PomodoroConfig): NextPhase {
    return when (current) {
        "FOCUS" -> {
            val done = cycle + 1
            if (done >= config.cycles.coerceAtLeast(1)) NextPhase("LONG_BREAK", done) else NextPhase("SHORT_BREAK", done)
        }
        "LONG_BREAK" -> NextPhase("FOCUS", 0)
        else -> NextPhase("FOCUS", cycle)
    }
}
