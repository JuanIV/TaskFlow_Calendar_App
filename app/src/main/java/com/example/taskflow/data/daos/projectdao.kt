package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.taskflow.data.entities.ProjectEntity
import com.example.taskflow.data.entities.ProjectExcludedDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProject(projectId: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND isTemplate = 0 ORDER BY createdAt ASC")
    fun getActiveProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND isTemplate = 0 ORDER BY createdAt ASC")
    suspend fun getActiveProjectsSync(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL AND isTemplate = 1 ORDER BY createdAt ASC")
    fun getTemplates(): Flow<List<ProjectEntity>>

    @Query("UPDATE projects SET deletedAt = :at WHERE id = :projectId")
    suspend fun softDeleteProject(projectId: Long, at: Long)

    @Query("UPDATE projects SET deletedAt = NULL WHERE id = :projectId")
    suspend fun restoreProject(projectId: Long)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun permanentlyDeleteProject(projectId: Long)

    @Insert
    suspend fun insertExcludedDays(days: List<ProjectExcludedDayEntity>)

    @Query("DELETE FROM project_excluded_days WHERE projectId = :projectId")
    suspend fun deleteExcludedDays(projectId: Long)

    @Query("SELECT epochDay FROM project_excluded_days WHERE projectId = :projectId")
    suspend fun getExcludedDays(projectId: Long): List<Long>
}
