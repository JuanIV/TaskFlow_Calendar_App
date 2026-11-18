package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.taskflow.data.entities.DayRecordEntity
import com.example.taskflow.data.entities.RoutineDayRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayRecord(record: DayRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineDayRecord(record: RoutineDayRecordEntity)

    @Query("SELECT * FROM day_records ORDER BY date DESC")
    fun getAllDayRecords(): Flow<List<DayRecordEntity>>

    @Query("SELECT * FROM day_records ORDER BY date DESC")
    suspend fun getAllDayRecordsSync(): List<DayRecordEntity>

    @Query("SELECT * FROM routine_day_records WHERE routineId = :routineId ORDER BY date DESC")
    fun getRecordsForRoutine(routineId: Long): Flow<List<RoutineDayRecordEntity>>

    @Query("SELECT * FROM routine_day_records WHERE routineId = :routineId ORDER BY date DESC")
    suspend fun getRecordsForRoutineSync(routineId: Long): List<RoutineDayRecordEntity>

    @Query("SELECT COUNT(*) FROM routine_day_records WHERE routineId = :routineId AND status = 'GRACE' AND date >= :startOfMonth AND date <= :endOfMonth")
    suspend fun countGraceDays(routineId: Long, startOfMonth: Long, endOfMonth: Long): Int

    @Query("SELECT * FROM routine_day_records")
    fun observeAllRoutineRecords(): Flow<List<RoutineDayRecordEntity>>

    @Query("DELETE FROM day_records")
    suspend fun clearDayRecords()

    @Query("DELETE FROM routine_day_records")
    suspend fun clearRoutineRecords()
}
