package com.example.taskflow.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.EventExceptionDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.expandEvents
import com.example.taskflow.domain.logicalDateNow
import com.example.taskflow.domain.normalizeText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// type: EVENT, TASK o ROUTINE. extra = fecha original (eventos). displayDate = fecha a mostrar
data class SearchResultItem(
    val type: String,
    val id: Long,
    val title: String,
    val displayDate: Long?,
    val extra: Long
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val eventDao: EventDao,
    private val exceptionDao: EventExceptionDao,
    private val routineDao: RoutineDao,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val searchResults: StateFlow<List<SearchResultItem>> = searchQuery
        .debounce(300)
        .map { search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(value: String) {
        searchQuery.value = value
    }

    private fun matches(q: String, vararg fields: String?): Boolean =
        fields.any { it != null && normalizeText(it).contains(q) }

    private suspend fun search(raw: String): List<SearchResultItem> {
        val q = normalizeText(raw.trim())
        if (q.isBlank()) return emptyList()
        val today = logicalDateNow(settingsRepo.getDayStartHourSync())
        val result = mutableListOf<SearchResultItem>()

        val events = eventDao.getActiveEventsFromSync(0L).filter { matches(q, it.title, it.notes, it.location) }.take(30)
        if (events.isNotEmpty()) {
            val exceptions = exceptionDao.getExceptionsForEvents(events.map { it.id })
            for (event in events) {
                val mine = exceptions.filter { it.eventId == event.id }
                val all = expandEvents(listOf(event), mine, minOf(event.date, today), today + 400)
                val chosen = all.firstOrNull { it.startDate >= today } ?: all.lastOrNull()
                result.add(SearchResultItem("EVENT", event.id, event.title, chosen?.startDate ?: event.date, chosen?.originalDate ?: event.date))
            }
        }

        taskDao.getAllActiveTasksSync()
            .filter { it.parentTaskId == null && matches(q, it.title, it.notes) }
            .forEach { result.add(SearchResultItem("TASK", it.id, it.title, it.date, 0L)) }

        for (routine in routineDao.getAllRoutinesSync()) {
            val taskMatch = routineDao.getTasksForRoutineSync(routine.id).any { matches(q, it.title) }
            if (matches(q, routine.name) || taskMatch) {
                result.add(SearchResultItem("ROUTINE", routine.id, routine.name, null, 0L))
            }
        }
        return result
    }
}
