package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.taskflow.data.entities.ProjectNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectNodeDao {
    @Insert
    suspend fun insertNode(node: ProjectNodeEntity): Long

    @Update
    suspend fun updateNode(node: ProjectNodeEntity)

    @Query("DELETE FROM project_nodes WHERE id = :nodeId")
    suspend fun deleteNode(nodeId: Long)

    @Query("SELECT * FROM project_nodes WHERE id = :nodeId")
    suspend fun getNodeById(nodeId: Long): ProjectNodeEntity?

    @Query("SELECT * FROM project_nodes WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    fun observeNodes(projectId: Long): Flow<List<ProjectNodeEntity>>

    @Query("SELECT * FROM project_nodes")
    fun observeAllNodes(): Flow<List<ProjectNodeEntity>>

    @Query("SELECT * FROM project_nodes WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    suspend fun getNodesSync(projectId: Long): List<ProjectNodeEntity>
}
