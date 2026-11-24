package com.example.taskflow.domain

// Evento de calendario generado a partir de un proyecto: deadline o hito. Solo lectura.
data class ProjectCalendarItem(
    val title: String,
    val day: Long,
    val colorArgb: Int,
    val isDeadline: Boolean
)

// Color fijo para los elementos de proyecto en el calendario
const val PROJECT_CALENDAR_COLOR = 0xFFB71C1C.toInt()

// Deadline de cada proyecto y dia de cada hito con fecha
fun projectCalendarItems(
    projectName: String,
    deadlineDate: Long?,
    milestones: List<Pair<String, Long>>
): List<ProjectCalendarItem> {
    val out = ArrayList<ProjectCalendarItem>()
    if (deadlineDate != null) {
        out.add(ProjectCalendarItem("Fecha limite: $projectName", deadlineDate, PROJECT_CALENDAR_COLOR, true))
    }
    for ((title, day) in milestones) {
        out.add(ProjectCalendarItem("Hito: $title", day, PROJECT_CALENDAR_COLOR, false))
    }
    return out
}
