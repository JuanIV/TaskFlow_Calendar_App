package com.example.taskflow.domain

import com.example.taskflow.data.entities.EventEntity

// Los eventos SECONDARY solo se ven en la vista DAY (y en la pantalla de dia, que no filtra).
fun EventEntity.isVisibleIn(viewType: String): Boolean {
    return this.priority != "SECONDARY" || viewType == "DAY"
}

fun EventOccurrence.isVisibleIn(viewType: String): Boolean {
    return this.priority != "SECONDARY" || viewType == "DAY"
}
