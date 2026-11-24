package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.taskflow.data.entities.ProjectDailyLogEntity

@Dao
interface ProjectDailyLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: ProjectDailyLogEntity)

    @Query("SELECT * FROM project_daily_log WHERE epochDay = :day")
    suspend fun getForDay(day: Long): List<ProjectDailyLogEntity>

    @Query("SELECT * FROM project_daily_log WHERE nodeId = :nodeId AND epochDay = :day")
    suspend fun getForNodeAndDay(nodeId: Long, day: Long): ProjectDailyLogEntity?

    @Query("SELECT * FROM project_daily_log WHERE epochDay >= :from AND epochDay <= :to")
    suspend fun getBetween(from: Long, to: Long): List<ProjectDailyLogEntity>
}
