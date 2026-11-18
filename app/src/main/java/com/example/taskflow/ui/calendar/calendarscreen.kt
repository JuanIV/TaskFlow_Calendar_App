package com.example.taskflow.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.taskflow.R
import com.example.taskflow.data.entities.TaskEntity
import com.example.taskflow.ui.events.EventDialog
import com.example.taskflow.ui.stats.StreakBadgeViewModel
import com.example.taskflow.ui.tasks.TaskDialog
import com.example.taskflow.ui.tasks.TasksViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    calendarViewModel: CalendarViewModel = hiltViewModel(),
    tasksViewModel: TasksViewModel = hiltViewModel(),
    onNavigateToDetail: (Long, Long) -> Unit,
    onOpenDay: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {}
) {
    val currentView by calendarViewModel.calendarView.collectAsState()
    val todayEpoch by calendarViewModel.todayEpoch.collectAsState()
    val firstDay by calendarViewModel.firstDay.collectAsState()
    val allTasks by tasksViewModel.allActiveTasks.collectAsState()
    var anchorEpoch by remember { mutableStateOf<Long?>(null) }

    val today = LocalDate.ofEpochDay(todayEpoch)
    val anchor = anchorEpoch?.let { LocalDate.ofEpochDay(it) } ?: today

    key(currentView, firstDay) {
        CalendarContent(
            view = currentView,
            today = today,
            anchor = anchor,
            firstDay = firstDay,
            allTasks = allTasks,
            calendarViewModel = calendarViewModel,
            tasksViewModel = tasksViewModel,
            onAnchorChange = { anchorEpoch = it.toEpochDay() },
            onNavigateToDetail = onNavigateToDetail,
            onOpenDay = onOpenDay,
            onOpenSettings = onOpenSettings,
            onOpenSearch = onOpenSearch
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CalendarContent(
    view: String,
    today: LocalDate,
    anchor: LocalDate,
    firstDay: DayOfWeek,
    allTasks: List<TaskEntity>,
    calendarViewModel: CalendarViewModel,
    tasksViewModel: TasksViewModel,
    onAnchorChange: (LocalDate) -> Unit,
    onNavigateToDetail: (Long, Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val streakViewModel: StreakBadgeViewModel = hiltViewModel()
    val globalStreak by streakViewModel.streak.collectAsState()
    val todayEpoch = today.toEpochDay()
    val pagerState = rememberPagerState(initialPage = pageForDate(view, today, anchor, firstDay), pageCount = { PAGER_COUNT })
    val hourHeight = 52.dp
    val density = LocalDensity.current
    val gridScroll = rememberScrollState(with(density) { (hourHeight * maxOf(0, LocalTime.now().hour - 1)).roundToPx() })
    val esLocale = remember { Locale("es", "ES") }

    var viewMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var eventDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    var eventDialogMinute by remember { mutableStateOf<Int?>(null) }
    var newTaskDate by remember { mutableStateOf<Long?>(null) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }

    val deletedMsg = stringResource(R.string.s2_task_deleted)
    val undoLabel = stringResource(R.string.s2_undo)

    val pendingByDate = remember(allTasks) {
        allTasks
            .filter { it.parentTaskId == null && it.date != null && !it.isCompleted }
            .groupingBy { it.date ?: 0L }
            .eachCount()
    }

    LaunchedEffect(pagerState, view) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (view != "AGENDA") onAnchorChange(anchorForPage(view, today, page, firstDay))
        }
    }

    val pageDate = dateForPage(view, today, pagerState.currentPage, firstDay)
    val title = when (view) {
        "MONTH" -> pageDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", esLocale)).replaceFirstChar { it.uppercase() }
        "WEEK" -> {
            val end = pageDate.plusDays(6)
            if (pageDate.month == end.month) {
                pageDate.dayOfMonth.toString() + " - " + end.format(DateTimeFormatter.ofPattern("d MMM yyyy", esLocale))
            } else {
                pageDate.format(DateTimeFormatter.ofPattern("d MMM", esLocale)) + " - " + end.format(DateTimeFormatter.ofPattern("d MMM yyyy", esLocale))
            }
        }
        "DAY" -> pageDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", esLocale)).replaceFirstChar { it.uppercase() }
        else -> stringResource(R.string.s2_view_agenda)
    }
    val fabDate = anchorForPage(view, today, pagerState.currentPage, firstDay)
    val onSlotClick: (LocalDate, Int) -> Unit = { date, minute ->
        eventDialogDate = date
        eventDialogMinute = minute
    }

    CompositionLocalProvider(LocalFirstDay provides firstDay) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (view != "AGENDA") {
                                IconButton(onClick = {
                                    scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                                }) { Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.s2_prev)) }
                            }
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (view != "AGENDA") {
                                IconButton(onClick = {
                                    scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(PAGER_COUNT - 1)) }
                                }) { Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.s2_next)) }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (view != "AGENDA") scope.launch { pagerState.animateScrollToPage(PAGER_CENTER) }
                        }) { Icon(Icons.Default.Today, contentDescription = stringResource(R.string.s2_today)) }
                        if (globalStreak > 0) {
                            Text(stringResource(R.string.s2_streak_badge, globalStreak), style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.s2_search)) }
                        Box {
                            IconButton(onClick = { viewMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.s2_menu)) }
                            DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.s2_view_month)) }, onClick = { calendarViewModel.setCalendarView("MONTH"); viewMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.s2_view_week)) }, onClick = { calendarViewModel.setCalendarView("WEEK"); viewMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.s2_view_day)) }, onClick = { calendarViewModel.setCalendarView("DAY"); viewMenu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.s2_view_agenda)) }, onClick = { calendarViewModel.setCalendarView("AGENDA"); viewMenu = false })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(stringResource(R.string.s2_settings)) }, onClick = { viewMenu = false; onOpenSettings() })
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                Box {
                    FloatingActionButton(onClick = { addMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.s2_add))
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.s2_new_event)) },
                            leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                            onClick = { addMenu = false; eventDialogMinute = null; eventDialogDate = fabDate }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.s2_new_task)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = { addMenu = false; newTaskDate = fabDate.toEpochDay() }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (view) {
                    "MONTH" -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        MonthPage(
                            month = YearMonth.from(dateForPage("MONTH", today, page, firstDay)),
                            todayEpoch = todayEpoch,
                            pendingTasksByDate = pendingByDate,
                            occurrencesFor = calendarViewModel::occurrencesFor,
                            onDayClick = { onOpenDay(it.toEpochDay()) }
                        )
                    }
                    "WEEK" -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        WeekPage(
                            weekStart = dateForPage("WEEK", today, page, firstDay),
                            todayEpoch = todayEpoch,
                            hourHeight = hourHeight,
                            scrollState = gridScroll,
                            occurrencesFor = calendarViewModel::occurrencesFor,
                            onDayClick = { onOpenDay(it.toEpochDay()) },
                            onEventClick = onNavigateToDetail,
                            onSlotClick = onSlotClick
                        )
                    }
                    "DAY" -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val date = dateForPage("DAY", today, page, firstDay)
                        DayPage(
                            date = date,
                            todayEpoch = todayEpoch,
                            hourHeight = hourHeight,
                            scrollState = gridScroll,
                            tasks = allTasks.filter { it.parentTaskId == null && it.date == date.toEpochDay() },
                            occurrencesFor = calendarViewModel::occurrencesFor,
                            onEventClick = onNavigateToDetail,
                            onSlotClick = onSlotClick,
                            onToggleTask = { tasksViewModel.toggleTaskCompletion(it) },
                            onTaskClick = { editingTask = it }
                        )
                    }
                    else -> AgendaView(
                        todayEpoch = todayEpoch,
                        tasks = allTasks,
                        occurrencesFor = calendarViewModel::occurrencesFor,
                        onEventClick = onNavigateToDetail,
                        onDayClick = { onOpenDay(it.toEpochDay()) },
                        onToggleTask = { tasksViewModel.toggleTaskCompletion(it) }
                    )
                }
            }
        }
    }

    val dialogDate = eventDialogDate
    if (dialogDate != null) {
        EventDialog(
            initialDate = dialogDate,
            initialStartMinute = eventDialogMinute,
            onDismiss = { eventDialogDate = null; eventDialogMinute = null }
        )
    }

    if (newTaskDate != null || editingTask != null) {
        val editing = editingTask
        TaskDialog(
            initialDate = newTaskDate,
            taskToEdit = editing,
            existingSubtasks = if (editing != null) allTasks.filter { it.parentTaskId == editing.id }.sortedBy { it.sortOrder } else emptyList(),
            onDismiss = { newTaskDate = null; editingTask = null },
            onSave = { result -> tasksViewModel.saveTaskFromResult(result) },
            onDelete = { id ->
                tasksViewModel.deleteTask(id)
                newTaskDate = null
                editingTask = null
                scope.launch {
                    val r = snackbarHostState.showSnackbar(message = deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) tasksViewModel.restoreTask(id)
                }
            },
            onToggleSubtask = { tasksViewModel.toggleTaskCompletion(it) },
            onDeleteSubtask = { tasksViewModel.deleteTask(it) }
        )
    }
}
