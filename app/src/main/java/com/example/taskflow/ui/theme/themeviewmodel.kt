package com.example.taskflow.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    private fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val themeMode: StateFlow<String> = repo.themeModeFlow.asState("SYSTEM")
    val dynamicColor: StateFlow<Boolean> = repo.dynamicColorFlow.asState(true)
    val accent: StateFlow<Int> = repo.accentColorFlow.asState(0xFF3949AB.toInt())
    val firstDay: StateFlow<Int> = repo.firstDayFlow.asState(1)
    val timeFormat: StateFlow<String> = repo.timeFormatFlow.asState("SYSTEM")
    val customPalette: StateFlow<List<Int>> = repo.customPaletteFlow.asState(emptyList())

    fun setThemeMode(value: String) { viewModelScope.launch { repo.setThemeMode(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { repo.setDynamicColor(value) } }
    fun setAccent(value: Int) { viewModelScope.launch { repo.setAccentColor(value) } }
    fun setFirstDay(value: Int) { viewModelScope.launch { repo.setFirstDay(value) } }
    fun setTimeFormat(value: String) { viewModelScope.launch { repo.setTimeFormat(value) } }

    fun addCustomColor(argb: Int) {
        viewModelScope.launch {
            val current = repo.customPaletteFlow.first()
            if (current.size < 12 && argb !in current) repo.setCustomPalette(current + argb)
        }
    }

    fun removeCustomColor(argb: Int) {
        viewModelScope.launch {
            repo.setCustomPalette(repo.customPaletteFlow.first().filter { it != argb })
        }
    }
}
