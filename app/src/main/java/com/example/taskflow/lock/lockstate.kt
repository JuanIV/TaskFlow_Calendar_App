package com.example.taskflow.lock

import com.example.taskflow.data.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

fun shouldLockAfterAbsence(enabled: Boolean, lastLeftMs: Long, nowMs: Long, timeoutSec: Int): Boolean {
    if (!enabled || lastLeftMs <= 0L) return false
    return nowMs - lastLeftMs >= timeoutSec * 1000L
}

// Estado del bloqueo de toda la app. Al arrancar el proceso siempre empieza bloqueado.
@Singleton
class LockState @Inject constructor(settingsRepo: SettingsRepository) {

    // null = todavia no se sabe
    val enabled = MutableStateFlow<Boolean?>(null)
    val locked = MutableStateFlow(true)

    @Volatile private var timeoutSec = 30
    @Volatile private var lastLeftMs = 0L

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settingsRepo.lockTimeoutFlow.collect { timeoutSec = it }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settingsRepo.lockEnabledFlow.collect { enabled.value = it }
        }
    }

    fun onLeft() {
        lastLeftMs = System.currentTimeMillis()
    }

    fun onReturned() {
        if (shouldLockAfterAbsence(enabled.value == true, lastLeftMs, System.currentTimeMillis(), timeoutSec)) {
            locked.value = true
        }
    }

    fun unlock() {
        locked.value = false
    }
}