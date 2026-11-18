package com.example.taskflow.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.TaskDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class TrashWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TrashWorkerEntryPoint {
        fun taskDao(): TaskDao
        fun eventDao(): EventDao
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, TrashWorkerEntryPoint::class.java)
            val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            entryPoint.taskDao().purgeDeletedBefore(threshold)
            entryPoint.eventDao().purgeDeletedBefore(threshold)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
