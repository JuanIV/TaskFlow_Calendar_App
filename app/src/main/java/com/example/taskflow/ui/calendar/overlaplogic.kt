package com.example.taskflow.ui.calendar

import com.example.taskflow.domain.EventOccurrence

data class PositionedEvent(
    val occurrence: EventOccurrence,
    val column: Int,
    val totalColumns: Int
)

private const val DEFAULT_DURATION = 30

private fun endOf(ev: EventOccurrence): Int {
    val start = ev.startMinute ?: 0
    val end = ev.endMinute
    return if (end != null && end > start) end else start + DEFAULT_DURATION
}

fun packEvents(events: List<EventOccurrence>): List<PositionedEvent> {
    val timed = events
        .filter { !it.allDay && it.startMinute != null }
        .sortedWith(compareBy({ it.startMinute }, { -endOf(it) }))

    val result = mutableListOf<PositionedEvent>()
    var cluster = mutableListOf<EventOccurrence>()
    var clusterEnd = -1

    fun flush() {
        if (cluster.isEmpty()) return
        val columnEnds = mutableListOf<Int>()
        val assignment = mutableListOf<Pair<EventOccurrence, Int>>()
        for (ev in cluster) {
            val start = ev.startMinute ?: 0
            var col = columnEnds.indexOfFirst { it <= start }
            if (col == -1) {
                columnEnds.add(endOf(ev))
                col = columnEnds.size - 1
            } else {
                columnEnds[col] = endOf(ev)
            }
            assignment.add(Pair(ev, col))
        }
        val total = columnEnds.size
        assignment.forEach { (ev, col) -> result.add(PositionedEvent(ev, col, total)) }
        cluster = mutableListOf()
    }

    for (ev in timed) {
        val start = ev.startMinute ?: 0
        if (cluster.isNotEmpty() && start >= clusterEnd) {
            flush()
            clusterEnd = -1
        }
        cluster.add(ev)
        clusterEnd = maxOf(clusterEnd, endOf(ev))
    }
    flush()
    return result
}
