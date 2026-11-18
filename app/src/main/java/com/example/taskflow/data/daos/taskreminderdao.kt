package com.example.taskflow.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.taskflow.data.entities.TaskReminderEntity

@Dao
interface TaskReminderDao {
    @Insert
    suspend fun insertAll(reminders: List<TaskReminderEntity>)

    @Query("DELETE FROM task_reminders WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId")
    suspend fun getForTaskSync(taskId: Long): List<TaskReminderEntity>

    @Query("SELECT * FROM task_reminders")
    suspend fun getAllSync(): List<TaskReminderEntity>

    @Transaction
    suspend fun replaceForTask(taskId: Long, reminders: List<TaskReminderEntity>) {
        deleteForTask(taskId)
        if (reminders.isNotEmpty()) {
            insertAll(reminders.map { it.copy(id = 0L, taskId = taskId) })
        }
    }
}
