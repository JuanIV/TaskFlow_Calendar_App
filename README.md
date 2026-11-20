# TaskFlow

**TaskFlow** is a productivity and time-management Android application built with **Kotlin**, **Jetpack Compose**, **Hilt**, and **Room**. It combines task management, calendar scheduling, routines, focus sessions, quick capture, full-screen alarms, and home-screen widgets into a single offline-first experience.

## Features

- **Tasks & Loose Tasks:** Manage daily tasks, subtasks, completion states, undo batches, and loose tasks with configurable reminder windows and overdue handling (`SHOW` or `MOVE_TO_TODAY`).
- **Calendar & Events:** View and manage events across day, week, month, and agenda layouts with recurring schedules, exceptions/series edits, reminders, importance levels, custom icons, and color palettes.
- **Routines & Streaks:** Track active routines, sequential routine steps, optional tasks, end-of-routine warnings, daily completion records, and streaks.
- **Focus Sessions:** Built-in focus timer and session tracking with dedicated notification channels and action receivers.
- **Quick Capture:** Fast entry via a Quick Settings tile (`QuickAddTileService`), app shortcuts (`shortcuts.xml`), and `QuickCaptureDialog`.
- **Full-Screen Alarms:** Alarm-mode event reminders (`AlarmActivity`) backed by high-priority full-screen notifications (`ALARM_SCREEN`) and wake/screen-on manifest configuration.
- **App Lock:** Absence-based lock timeout (`LockState`, `LockScreen`, `LockSection`) with unit test coverage (`LockStateTest`).
- **Widgets, Stats, Trash & Backup:** Home-screen widgets (`TodayTasksWidgetReceiver`, `AgendaWidgetReceiver`), statistics screens, soft-delete trash with background cleanup (`TrashWorker`), history view, and database export/import backup (`BackupManager`).

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Dependency Injection:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`)
- **Local Persistence:** Room (with schema exports in `app/schemas/` and migration tests)
- **Background & System Integration:** Alarm scheduling/receivers, Boot receiver, Quick Settings Tile, AppWidgets, and WorkManager

## Project Structure

```text
app/src/main/java/com/example/taskflow/
├── alarm/          # Full-screen alarm activity
├── capture/        # Quick capture contract & Quick Settings tile service
├── data/           # Room database, DAOs, entities, repositories, backup manager
├── di/             # Hilt database and scheduling modules
├── domain/         # Pure business logic, recurrence, streaks, evaluators, formatters
├── focus/          # Focus state, manager, logic, and receivers
├── notifications/  # Channels, IDs, refresher, and notification action receivers
├── scheduling/     # Alarm contract, scheduler, receivers, and coordinator
├── ui/             # Compose screens, ViewModels, dialogs, navigation, and themes
├── widgets/        # Today tasks and agenda AppWidgets
└── workers/        # Background workers (e.g., trash cleanup)
