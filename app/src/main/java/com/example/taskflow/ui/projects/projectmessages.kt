package com.example.taskflow.ui.projects

import android.content.Context
import android.widget.Toast
import com.example.taskflow.R
import com.example.taskflow.domain.OpResult
import com.example.taskflow.domain.PlanStatus
import com.example.taskflow.domain.ProjectStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Texto de error para un resultado de operacion. null si es OK. Version no composable.
fun opResultMessage(context: Context, result: OpResult): String? = when (result) {
    OpResult.OK -> null
    OpResult.EMPTY_NAME -> context.getString(R.string.p1b_err_name)
    OpResult.INVALID_CAPACITY -> context.getString(R.string.p1b_err_capacity)
    OpResult.INVALID_DAYS -> context.getString(R.string.p1b_err_days)
    OpResult.INVALID_DEADLINE -> context.getString(R.string.p1b_err_deadline)
    OpResult.EMPTY_TITLE -> context.getString(R.string.p1b_err_title)
    OpResult.INVALID_MINUTES -> context.getString(R.string.p1b_err_minutes)
    OpResult.PARENT_IS_MILESTONE -> context.getString(R.string.p1b_err_parent_milestone)
    OpResult.PARENT_HAS_PROGRESS -> context.getString(R.string.p1b_err_parent_progress)
    OpResult.CYCLE -> context.getString(R.string.p1b_err_cycle)
    OpResult.NOT_FOUND -> context.getString(R.string.p1b_err_not_found)
    OpResult.IS_GROUP -> context.getString(R.string.p1b_err_is_group)
    OpResult.INVALID_STATUS -> context.getString(R.string.p1b_err_status)
    OpResult.NEEDS_CONFIRMATION -> context.getString(R.string.p1b_err_needs_confirm)
}

// Muestra un aviso breve si el resultado no es OK
fun showOpResult(context: Context, result: OpResult) {
    val text = opResultMessage(context, result)
    if (text != null) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

fun formatDay(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

// Texto de estado de un proyecto frente a su deadline
fun projectStatusMessage(
    context: Context,
    projectStatus: String,
    planStatus: String,
    marginDays: Int?,
    overdueDays: Int
): String = when (projectStatus) {
    ProjectStatus.PAUSED -> context.getString(R.string.p1c_status_paused)
    ProjectStatus.COMPLETED -> context.getString(R.string.p1c_status_completed)
    ProjectStatus.ARCHIVED -> context.getString(R.string.p1c_status_archived)
    else -> when (planStatus) {
        PlanStatus.OK -> {
            val m = marginDays ?: 0
            context.resources.getQuantityString(R.plurals.p1c_margin_days, m, m)
        }
        PlanStatus.JUSTO -> context.getString(R.string.p1c_status_just)
        PlanStatus.ATRASADO -> context.resources.getQuantityString(R.plurals.p1c_overdue_days, overdueDays, overdueDays)
        PlanStatus.SIN_DEADLINE -> context.getString(R.string.p1c_status_no_deadline)
        PlanStatus.SIN_PENDIENTE -> context.getString(R.string.p1c_status_done_plan)
        PlanStatus.SIN_CAPACIDAD -> context.getString(R.string.p1c_status_no_capacity)
        PlanStatus.CICLO -> context.getString(R.string.p2a_status_cycle)
        else -> context.getString(R.string.p1c_status_paused)
    }
}
