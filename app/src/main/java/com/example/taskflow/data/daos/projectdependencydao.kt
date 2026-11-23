package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.taskflow.data.entities.ProjectDependencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDependencyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dependency: ProjectDependencyEntity)

    @Query("DELETE FROM project_dependencies WHERE predecessorId = :predecessorId AND successorId = :successorId")
    suspend fun delete(predecessorId: Long, successorId: Long)

    @Query("SELECT d.* FROM project_dependencies d INNER JOIN project_nodes n ON d.successorId = n.id WHERE n.projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<ProjectDependencyEntity>

    @Query("SELECT d.* FROM project_dependencies d INNER JOIN project_nodes n ON d.successorId = n.id WHERE n.projectId = :projectId")
    fun observeForProject(projectId: Long): Flow<List<ProjectDependencyEntity>>

    @Query("SELECT d.* FROM project_dependencies d INNER JOIN project_nodes n ON d.successorId = n.id")
    fun observeAll(): Flow<List<ProjectDependencyEntity>>
}
