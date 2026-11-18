package com.example.taskflow.di

import com.example.taskflow.scheduling.AlarmScheduler
import com.example.taskflow.scheduling.AlarmSchedulerImpl
import com.example.taskflow.scheduling.SchedulingCoordinator
import com.example.taskflow.scheduling.SchedulingCoordinatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulingModule {
    @Binds
    abstract fun bindSchedulingCoordinator(
        impl: SchedulingCoordinatorImpl
    ): SchedulingCoordinator

    @Binds
    abstract fun bindAlarmScheduler(
        impl: AlarmSchedulerImpl
    ): AlarmScheduler
}
