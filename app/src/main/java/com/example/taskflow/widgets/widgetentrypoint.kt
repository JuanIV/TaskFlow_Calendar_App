package com.example.taskflow.widgets

import android.content.Context
import com.example.taskflow.data.daos.EventDao
import com.example.taskflow.data.daos.EventExceptionDao
import com.example.taskflow.data.daos.TaskDao
import com.example.taskflow.data.daos.RoutineDao
import com.example.taskflow.data.daos.RecordDao
import com.example.taskflow.data.daos.FocusLogDao
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.TaskCompletionHelper
import com.example.taskflow.domain.TodayStateEvaluator
import com.example.taskflow.scheduling.SchedulingCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun taskDao(): TaskDao
    fun routineDao(): RoutineDao
    fun recordDao(): RecordDao
    fun focusLogDao(): FocusLogDao
    fun todayState(): TodayStateEvaluator
    fun eventDao(): EventDao
    fun exceptionDao(): EventExceptionDao
    fun settingsRepo(): SettingsRepository
    fun coordinator(): SchedulingCoordinator
    fun completion(): TaskCompletionHelper
}

fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
