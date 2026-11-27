package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.taskflow.data.entities.ProjectCellAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectCellDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cells: List<ProjectCellAssignmentEntity>)

    @Query("DELETE FROM project_cell_assignments WHERE nodeId IN (SELECT id FROM project_nodes WHERE projectId = :projectId)")
    suspend fun deleteForProject(projectId: Long)

    @Query("DELETE FROM project_cell_assignments WHERE nodeId = :nodeId")
    suspend fun deleteForNode(nodeId: Long)

    @Query("SELECT a.* FROM project_cell_assignments a INNER JOIN project_nodes n ON a.nodeId = n.id WHERE n.projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<ProjectCellAssignmentEntity>

    @Query("SELECT a.* FROM project_cell_assignments a INNER JOIN project_nodes n ON a.nodeId = n.id WHERE n.projectId = :projectId")
    fun observeForProject(projectId: Long): Flow<List<ProjectCellAssignmentEntity>>
}
