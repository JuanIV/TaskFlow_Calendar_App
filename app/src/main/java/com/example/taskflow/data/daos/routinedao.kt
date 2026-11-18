package com.example.taskflow.data.daos

import androidx.room.*
import com.example.taskflow.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutine(routineId: Long)

    @Query("SELECT * FROM routines WHERE isTemplate = 0 ORDER BY createdAt ASC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE isTemplate = 1 ORDER BY createdAt ASC")
    fun getTemplates(): Flow<List<RoutineEntity>>

    @Insert
    suspend fun insertRoutineTask(task: RoutineTaskEntity)

    @Update
    suspend fun updateRoutineTask(task: RoutineTaskEntity)

    @Query("SELECT * FROM routine_tasks WHERE routineId = :routineId AND deletedAtEpochDay IS NULL ORDER BY sortOrder ASC")
    fun getTasksForRoutine(routineId: Long): Flow<List<RoutineTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskCompletion(completion: RoutineTaskCompletionEntity)

    @Query("DELETE FROM routine_task_completions WHERE routineTaskId = :taskId AND date = :date")
    suspend fun deleteTaskCompletion(taskId: Long, date: Long)

    @Query("SELECT * FROM routine_task_completions WHERE routineTaskId IN (SELECT id FROM routine_tasks WHERE routineId = :routineId) AND date = :date")
    fun getCompletionsForRoutineOnDate(routineId: Long, date: Long): Flow<List<RoutineTaskCompletionEntity>>

    @Query("SELECT * FROM routines WHERE isActive = 1 AND isTemplate = 0 ORDER BY createdAt ASC")
    suspend fun getActiveRoutinesSync(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE isTemplate = 0")
    suspend fun getAllRoutinesSync(): List<RoutineEntity>

    @Query("SELECT * FROM routine_tasks WHERE routineId = :routineId AND deletedAtEpochDay IS NULL ORDER BY sortOrder ASC")
    suspend fun getTasksForRoutineSync(routineId: Long): List<RoutineTaskEntity>

    @Query("SELECT * FROM routine_task_completions WHERE date = :date")
    suspend fun getAllCompletionsOnDateSync(date: Long): List<RoutineTaskCompletionEntity>

    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutineById(routineId: Long): RoutineEntity?

    @Query("SELECT * FROM routine_task_completions WHERE date = :date")
    fun observeCompletionsOnDate(date: Long): Flow<List<RoutineTaskCompletionEntity>>

    @Query("SELECT * FROM routine_tasks WHERE deletedAtEpochDay IS NULL ORDER BY sortOrder ASC")
    fun observeAllTasks(): Flow<List<RoutineTaskEntity>>

    @Query("SELECT * FROM routine_task_completions")
    fun observeAllCompletions(): Flow<List<RoutineTaskCompletionEntity>>
}
