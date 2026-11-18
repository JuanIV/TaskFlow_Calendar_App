package com.example.taskflow.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2() {
        var db = helper.createDatabase(testDb, 1)
        db.execSQL("INSERT INTO events (id, title, date, allDay, colorArgb, createdAt) VALUES (1, 'E', 19000, 1, 0, 0)")
        db.execSQL("INSERT INTO tasks (id, title, isCompleted, sortOrder, createdAt) VALUES (1, 'T', 0, 0, 0)")
        db.close()
        db = helper.runMigrationsAndValidate(testDb, 2, true, AppDatabase.MIGRATION_1_2)
        val cursor = db.query("SELECT priority FROM tasks WHERE id = 1")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()
    }

    @Test
    fun migrate2To3() {
        var db = helper.createDatabase(testDb, 2)
        db.close()
        db = helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)
        db.close()
    }

    @Test
    fun migrate3To4() {
        var db = helper.createDatabase(testDb, 3)
        db.execSQL(
            "INSERT INTO events (id, title, date, allDay, colorArgb, createdAt, recurrenceType, recurrenceInterval, recurrenceWeekdaysMask, recurrenceEndType, priority) " +
                "VALUES (1, 'A', 19000, 1, 0, 0, 'NONE', 1, 0, 'NEVER', 'NORMAL')"
        )
        db.execSQL(
            "INSERT INTO events (id, title, date, allDay, colorArgb, createdAt, recurrenceType, recurrenceInterval, recurrenceWeekdaysMask, recurrenceEndType, priority) " +
                "VALUES (2, 'B', 19000, 1, 0, 0, 'NONE', 1, 0, 'NEVER', 'SECONDARY')"
        )
        db.close()
        db = helper.runMigrationsAndValidate(testDb, 4, true, AppDatabase.MIGRATION_3_4)
        val cursor = db.query("SELECT priority FROM events ORDER BY id")
        cursor.moveToFirst()
        assertEquals("IMPORTANT", cursor.getString(0))
        cursor.moveToNext()
        assertEquals("SECONDARY", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrate4To5() {
        var db = helper.createDatabase(testDb, 4)
        db.execSQL("INSERT INTO tasks (id, title, isCompleted, sortOrder, createdAt, priority) VALUES (1, 'T', 0, 0, 0, 0)")
        db.close()
        db = helper.runMigrationsAndValidate(testDb, 5, true, AppDatabase.MIGRATION_4_5)
        val cursor = db.query("SELECT recurrenceType, recurrenceInterval FROM tasks WHERE id = 1")
        cursor.moveToFirst()
        assertEquals("NONE", cursor.getString(0))
        assertEquals(1, cursor.getInt(1))
        cursor.close()
    }
}
