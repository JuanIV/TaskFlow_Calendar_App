package com.example.taskflow.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.SampleDataLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val sampleDataLoader: SampleDataLoader
) : ViewModel() {

    // null = todavia no se sabe
    val done: StateFlow<Boolean?> = settingsRepo.onboardingDoneFlow
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var started = false

    fun finish(loadSample: Boolean) {
        if (started) return
        started = true
        viewModelScope.launch {
            if (loadSample) sampleDataLoader.load()
            settingsRepo.setOnboardingDone(true)
        }
    }
}
