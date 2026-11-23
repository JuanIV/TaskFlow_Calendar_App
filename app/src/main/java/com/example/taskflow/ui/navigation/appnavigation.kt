package com.example.taskflow.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.taskflow.R
import com.example.taskflow.ui.calendar.CalendarScreen
import com.example.taskflow.ui.planner.PlannerScreen
import com.example.taskflow.ui.achievements.AchievementsScreen
import com.example.taskflow.ui.day.DayScreen
import com.example.taskflow.ui.events.EventDetailScreen
import com.example.taskflow.ui.focus.FocusScreen
import com.example.taskflow.ui.routines.RoutineDetailScreen
import com.example.taskflow.ui.routines.RoutinesScreen
import com.example.taskflow.ui.routines.TemplatesScreen
import com.example.taskflow.ui.search.SearchScreen
import com.example.taskflow.ui.settings.HistoryScreen
import com.example.taskflow.ui.settings.SettingsScreen
import com.example.taskflow.ui.settings.TrashScreen
import com.example.taskflow.ui.stats.StatsDetailScreen
import com.example.taskflow.ui.stats.StatsScreen
import com.example.taskflow.ui.tasks.TasksScreen
import com.example.taskflow.ui.projects.ProjectDetailScreen
import com.example.taskflow.ui.projects.ProjectGanttScreen
import com.example.taskflow.ui.projects.ProjectsScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: @Composable () -> Unit) {
    object Calendar : Screen("calendar", R.string.tab_calendar, { Icon(Icons.Default.CalendarMonth, null) })
    object Tasks : Screen("tasks", R.string.tab_tasks, { Icon(Icons.Default.List, null) })
    object Projects : Screen("projects", R.string.tab_projects, { Icon(Icons.Default.Folder, null) })
    object Routines : Screen("routines", R.string.tab_routines, { Icon(Icons.Default.Repeat, null) })
    object Statistics : Screen("statistics", R.string.tab_stats, { Icon(Icons.Default.BarChart, null) })
}

@Composable
fun AppNavigation(
    pendingRoute: String? = null,
    onRouteHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Calendar, Screen.Tasks, Screen.Projects, Screen.Routines, Screen.Statistics)

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute)
            onRouteHandled()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = screen.icon,
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Calendar.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onNavigateToDetail = { eventId, originalDate ->
                        navController.navigate("event_detail/$eventId/$originalDate")
                    },
                    onOpenDay = { epochDay -> navController.navigate("day/$epochDay") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenSearch = { navController.navigate("search") }
                )
            }
            composable(
                route = "day/{epochDay}",
                arguments = listOf(navArgument("epochDay") { type = NavType.LongType })
            ) { backStackEntry ->
                DayScreen(
                    epochDay = backStackEntry.arguments?.getLong("epochDay") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onEventClick = { eventId, originalDate -> navController.navigate("event_detail/$eventId/$originalDate") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrash = { navController.navigate("trash") },
                    onOpenHistory = { navController.navigate("history") }
                )
            }
            composable("trash") {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            composable("history") {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "focus?taskId={taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = -1L })
            ) {
                FocusScreen(onBack = { navController.popBackStack() })
            }
            composable("templates") {
                TemplatesScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Statistics.route) {
                StatsScreen(
                    onNavigateToDetail = { id -> navController.navigate("statistics_detail/$id") },
                    onOpenAchievements = { navController.navigate("achievements") }
                )
            }
            composable(
                route = "statistics_detail/{routineId}",
                arguments = listOf(navArgument("routineId") { type = NavType.LongType })
            ) {
                StatsDetailScreen(onBack = { navController.popBackStack() })
            }
            composable("achievements") {
                AchievementsScreen(onBack = { navController.popBackStack() })
            }
            composable("planner") {
                PlannerScreen(onBack = { navController.popBackStack() })
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToEvent = { eventId, originalDate -> navController.navigate("event_detail/$eventId/$originalDate") },
                    onNavigateToRoutine = { routineId -> navController.navigate("routine_detail/$routineId") }
                )
            }
            composable(
                route = "event_detail/{eventId}/{originalDate}",
                arguments = listOf(
                    navArgument("eventId") { type = NavType.LongType },
                    navArgument("originalDate") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getLong("eventId") ?: 0L
                val originalDate = backStackEntry.arguments?.getLong("originalDate") ?: 0L
                EventDetailScreen(
                    eventId = eventId,
                    originalDate = originalDate,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Tasks.route) {
                TasksScreen(
                    onOpenSearch = { navController.navigate("search") },
                    onOpenFocus = { taskId -> navController.navigate("focus?taskId=" + taskId) },
                    onOpenPlanner = { navController.navigate("planner") }
                )
            }
            composable(Screen.Projects.route) {
                ProjectsScreen(
                    onOpenProject = { projectId -> navController.navigate("project_detail/$projectId") }
                )
            }
            composable(
                route = "project_detail/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) {
                ProjectDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGantt = { projectId -> navController.navigate("project_gantt/$projectId") }
                )
            }
            composable(
                route = "project_gantt/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
            ) {
                ProjectGanttScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Routines.route) {
                RoutinesScreen(
                    onNavigateToDetail = { id -> navController.navigate("routine_detail/$id") },
                    onNavigateToTemplates = { navController.navigate("templates") },
                    onOpenSearch = { navController.navigate("search") }
                )
            }
            composable(
                route = "routine_detail/{routineId}",
                arguments = listOf(navArgument("routineId") { type = NavType.LongType })
            ) {
                RoutineDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun EmptyScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title)
    }
}
