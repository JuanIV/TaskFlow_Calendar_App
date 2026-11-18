package com.example.taskflow.domain

import androidx.compose.ui.graphics.toArgb
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.entities.EventEntity
import com.example.taskflow.data.entities.EventReminderEntity
import com.example.taskflow.data.entities.RoutineEntity
import com.example.taskflow.data.entities.RoutineTaskEntity
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.scheduling.SchedulingCoordinator
import javax.inject.Inject
import javax.inject.Singleton

// Inserta unos pocos datos de ejemplo para que la app no se vea vacia la primera vez.
@Singleton
class SampleDataLoader @Inject constructor(
    private val taskDao: TaskDao,
    private val eventDao: EventDao,
    private val routineDao: RoutineDao,
    private val settingsRepo: SettingsRepository,
    private val coordinator: SchedulingCoordinator
) {
    suspend fun load() {
        val now = System.currentTimeMillis()
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())

        // Rutinas
        val morningId = routineDao.insertRoutine(
            RoutineEntity(
                name = "Rutina de mañana",
                colorArgb = EventColors[10].toArgb(),
                recurrenceType = "WEEKDAYS",
                weekdaysMask = 127,
                intervalDays = 1,
                anchorDate = today,
                startMinuteOfDay = 480,
                createdAt = now,
                icon = "sun"
            )
        )
        listOf("Beber un vaso de agua", "Estirar 5 minutos", "Revisar la agenda del día").forEachIndexed { index, title ->
            routineDao.insertRoutineTask(RoutineTaskEntity(routineId = morningId, title = title, sortOrder = index))
        }

        val workoutId = routineDao.insertRoutine(
            RoutineEntity(
                name = "Ejercicio",
                colorArgb = EventColors[5].toArgb(),
                recurrenceType = "WEEKDAYS",
                weekdaysMask = 21,
                intervalDays = 1,
                anchorDate = today,
                startMinuteOfDay = 1080,
                createdAt = now,
                icon = "fitness",
                sequentialMode = true
            )
        )
        listOf("Calentamiento", "Entrenamiento", "Estiramientos").forEachIndexed { index, title ->
            routineDao.insertRoutineTask(RoutineTaskEntity(routineId = workoutId, title = title, sortOrder = index))
        }

        // Eventos
        eventDao.insertEventWithReminders(
            EventEntity(
                title = "Comida con amigos",
                date = today + 1,
                allDay = false,
                startMinuteOfDay = 840,
                endMinuteOfDay = 930,
                colorArgb = EventColors[10].toArgb(),
                createdAt = now,
                icon = "food"
            ),
            listOf(EventReminderEntity(eventId = 0L, offsetMinutes = 60))
        )
        eventDao.insertEventWithReminders(
            EventEntity(
                title = "Cita médica",
                date = today + 3,
                allDay = false,
                startMinuteOfDay = 600,
                endMinuteOfDay = 660,
                colorArgb = EventColors[0].toArgb(),
                createdAt = now,
                icon = "health"
            ),
            listOf(EventReminderEntity(eventId = 0L, offsetMinutes = 60))
        )
        eventDao.insertEventWithReminders(
            EventEntity(
                title = "Escapada de fin de semana",
                date = today + 6,
                endDate = today + 7,
                allDay = true,
                colorArgb = EventColors[7].toArgb(),
                createdAt = now,
                icon = "flight"
            ),
            emptyList()
        )

        // Tareas
        val reportId = taskDao.insertTask(
            TaskEntity(title = "Preparar el informe semanal", date = today, createdAt = now, priority = 3, estimatedMinutes = 60, sortOrder = 0)
        )
        listOf("Reunir los datos", "Redactar el borrador").forEachIndexed { index, title ->
            taskDao.insertTask(TaskEntity(title = title, date = today, createdAt = now, parentTaskId = reportId, sortOrder = index))
        }
        taskDao.insertTask(TaskEntity(title = "Comprar fruta", date = today, createdAt = now, estimatedMinutes = 15, sortOrder = 1))
        taskDao.insertTask(
            TaskEntity(
                title = "Regar las plantas",
                date = today,
                createdAt = now,
                estimatedMinutes = 10,
                recurrenceType = "WEEKLY",
                recurrenceInterval = 1,
                sortOrder = 2
            )
        )
        taskDao.insertTask(TaskEntity(title = "Llamar a la familia", createdAt = now, sortOrder = 3))
        taskDao.insertTask(TaskEntity(title = "Ordenar el escritorio", createdAt = now, sortOrder = 4))

        coordinator.onDataChanged()
    }
}
