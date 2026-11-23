package com.example.taskflow.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.EventExceptionDao
import com.example.taskflow.data.daos.FocusLogDao
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.daos.TaskReminderDao
import com.example.taskflow.data.daos.ProjectDao
import com.example.taskflow.data.daos.ProjectNodeDao
import com.example.taskflow.data.daos.ProjectDependencyDao
import com.example.taskflow.data.entities.*

@Database(
    entities = [
        EventEntity::class, EventReminderEntity::class, EventExceptionEntity::class,
        TaskEntity::class, TaskReminderEntity::class,
        RoutineEntity::class, RoutineTaskEntity::class, RoutineTaskCompletionEntity::class,
        RoutineDayRecordEntity::class, DayRecordEntity::class, FocusLogEntity::class,
        ProjectEntity::class, ProjectNodeEntity::class, ProjectExcludedDayEntity::class,
        ProjectDependencyEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun eventExceptionDao(): EventExceptionDao
    abstract fun taskDao(): TaskDao
    abstract fun taskReminderDao(): TaskReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun recordDao(): RecordDao
    abstract fun focusLogDao(): FocusLogDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectNodeDao(): ProjectNodeDao
    abstract fun projectDependencyDao(): ProjectDependencyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // events
                db.execSQL("ALTER TABLE events ADD COLUMN endDate INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN endMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN location TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceWeekdaysMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceEndType TEXT NOT NULL DEFAULT 'NEVER'")
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceUntil INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN recurrenceCount INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN deletedAt INTEGER")

                // event_exceptions
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `event_exceptions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventId` INTEGER NOT NULL,
                        `originalDate` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `overrideTitle` TEXT NOT NULL,
                        `overrideDate` INTEGER NOT NULL,
                        `overrideEndDate` INTEGER,
                        `overrideAllDay` INTEGER NOT NULL,
                        `overrideStartMinute` INTEGER,
                        `overrideEndMinute` INTEGER,
                        `overrideColorArgb` INTEGER NOT NULL,
                        `overrideNotes` TEXT,
                        `overrideLocation` TEXT,
                        FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_exceptions_eventId` ON `event_exceptions` (`eventId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_exceptions_eventId_originalDate` ON `event_exceptions` (`eventId`, `originalDate`)")

                // tasks
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tasks_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `date` INTEGER,
                        `isCompleted` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL DEFAULT 0,
                        `dueMinuteOfDay` INTEGER,
                        `notes` TEXT,
                        `parentTaskId` INTEGER,
                        `originalDate` INTEGER,
                        `deletedAt` INTEGER,
                        FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO tasks_new (id, title, date, isCompleted, completedAt, sortOrder, createdAt) SELECT id, title, date, isCompleted, completedAt, sortOrder, createdAt FROM tasks")
                db.execSQL("DROP TABLE tasks")
                db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)")

                // task_reminders
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `offsetMinutes` INTEGER,
                        `absEpochDay` INTEGER,
                        `absMinuteOfDay` INTEGER,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` ON `task_reminders` (`taskId`)")

                // routines
                db.execSQL("ALTER TABLE routines ADD COLUMN endMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN endWarningMinutes INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN startDate INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN endDate INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN pausedFromEpochDay INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN pausedUntilEpochDay INTEGER")
                db.execSQL("ALTER TABLE routines ADD COLUMN graceDaysPerMonth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE routines ADD COLUMN isTemplate INTEGER NOT NULL DEFAULT 0")

                // routine_tasks
                db.execSQL("ALTER TABLE routine_tasks ADD COLUMN isOptional INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE routine_tasks ADD COLUMN weekdaysMask INTEGER")

                // routine_day_records
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `routine_day_records` (
                        `routineId` INTEGER NOT NULL,
                        `date` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `requiredCount` INTEGER NOT NULL,
                        `doneCount` INTEGER NOT NULL,
                        PRIMARY KEY(`routineId`, `date`),
                        FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_day_records_routineId` ON `routine_day_records` (`routineId`)")

                // day_records
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `day_records` (
                        `date` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `requiredCount` INTEGER NOT NULL,
                        `doneCount` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE events SET priority = 'IMPORTANT' WHERE priority != 'SECONDARY'")
                db.execSQL("ALTER TABLE event_exceptions ADD COLUMN overrideImportance TEXT NOT NULL DEFAULT 'IMPORTANT'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN icon TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN alarmMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE routines ADD COLUMN icon TEXT")
                db.execSQL("ALTER TABLE routines ADD COLUMN sequentialMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMinutes INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceWeekdaysMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceSpawned INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER, `startedAt` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, `type` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_logs_taskId` ON `focus_logs` (`taskId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `projects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `colorArgb` INTEGER NOT NULL,
                        `icon` TEXT,
                        `startDate` INTEGER NOT NULL,
                        `deadlineDate` INTEGER,
                        `dailyCapacityMinutes` INTEGER NOT NULL,
                        `workingDaysMask` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `isTemplate` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `project_excluded_days` (
                        `projectId` INTEGER NOT NULL,
                        `epochDay` INTEGER NOT NULL,
                        PRIMARY KEY(`projectId`, `epochDay`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_excluded_days_projectId` ON `project_excluded_days` (`projectId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `project_nodes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `parentId` INTEGER,
                        `title` TEXT NOT NULL,
                        `notes` TEXT,
                        `sortOrder` INTEGER NOT NULL,
                        `isMilestone` INTEGER NOT NULL,
                        `estimatedMinutes` INTEGER NOT NULL,
                        `doneMinutes` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `actualMinutes` INTEGER,
                        `priority` INTEGER NOT NULL,
                        `colorArgb` INTEGER,
                        `notBeforeDate` INTEGER,
                        `pulledForwardDay` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`parentId`) REFERENCES `project_nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_nodes_projectId` ON `project_nodes` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_nodes_parentId` ON `project_nodes` (`parentId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `project_dependencies` (
                        `predecessorId` INTEGER NOT NULL,
                        `successorId` INTEGER NOT NULL,
                        PRIMARY KEY(`predecessorId`, `successorId`),
                        FOREIGN KEY(`predecessorId`) REFERENCES `project_nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`successorId`) REFERENCES `project_nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_dependencies_successorId` ON `project_dependencies` (`successorId`)")
            }
        }
    }
}
