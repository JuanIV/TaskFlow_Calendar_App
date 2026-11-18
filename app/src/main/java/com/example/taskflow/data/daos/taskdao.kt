package com.example.taskflow.data.daos

import androidx.room.*
import com.example.taskflow.data.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET deletedAt = :deletedAt WHERE id = :taskId OR parentTaskId = :taskId")
    suspend fun softDeleteTaskAndSubtasks(taskId: Long, deletedAt: Long)

    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :taskId OR parentTaskId = :taskId")
    suspend fun restoreTask(taskId: Long)

    @Query("UPDATE tasks SET isCompleted = 0, completedAt = NULL WHERE id = :taskId")
    suspend fun markPending(taskId: Long)

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId IS NULL AND date IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun getLooseTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId IS NULL AND date = :targetDate ORDER BY sortOrder ASC, createdAt ASC")
    fun getTasksForDate(targetDate: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    suspend fun getAllActiveTasksSync(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId = :parentId ORDER BY sortOrder ASC")
    fun getSubtasksForTask(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId = :parentId")
    suspend fun getSubtasksForTaskSync(parentId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE date = :targetDate AND deletedAt IS NULL")
    suspend fun getTasksForDateSync(targetDate: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId IS NULL AND date IS NOT NULL AND date <= :maxDate ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getTasksUpToDateSync(maxDate: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId IS NULL AND date IS NULL AND isCompleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getPendingLooseTasksSync(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId IS NULL AND isCompleted = 0")
    suspend fun getPendingTopLevelSync(): List<TaskEntity>

    @Query("UPDATE tasks SET originalDate = COALESCE(originalDate, date), date = :today WHERE deletedAt IS NULL AND parentTaskId IS NULL AND isCompleted = 0 AND date IS NOT NULL AND date < :today")
    suspend fun moveOverdueToDate(today: Long)

    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL AND parentTaskId IS NULL ORDER BY deletedAt DESC")
    fun getDeletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 AND deletedAt IS NULL AND parentTaskId IS NULL ORDER BY completedAt DESC")
    fun getCompletedHistory(): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeDeletedBefore(threshold: Long)

    @Query("DELETE FROM tasks WHERE id = :taskId OR parentTaskId = :taskId")
    suspend fun permanentlyDeleteTask(taskId: Long)
}
