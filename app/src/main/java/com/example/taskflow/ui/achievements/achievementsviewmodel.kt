package com.example.taskflow.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.achievements.AchievementManager
import com.example.taskflow.data.repositories.SettingsRepository
import com.example.taskflow.domain.ACHIEVEMENT_DEFS
import com.example.taskflow.domain.AchievementStats
import com.example.taskflow.domain.achievementMetric
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AchievementItem(
    val id: String,
    val metric: String,
    val target: Int,
    val current: Int,
    val unlocked: Boolean,
    val unlockedAt: Long?
)

data class AchievementsUi(
    val stats: AchievementStats = AchievementStats(0, 0, 0, 0, 0, 0),
    val items: List<AchievementItem> = emptyList()
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val manager: AchievementManager,
    settingsRepo: SettingsRepository
) : ViewModel() {

    val ui: StateFlow<AchievementsUi> = settingsRepo.achievementsFlow.map { entries ->
        val stats = manager.computeStats()
        val at = entries.associate { it.substringBefore("|") to it.substringAfter("|", "").toLongOrNull() }
        AchievementsUi(
            stats = stats,
            items = ACHIEVEMENT_DEFS.map { d ->
                AchievementItem(d.id, d.metric, d.target, achievementMetric(stats, d.metric), at.containsKey(d.id), at[d.id])
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AchievementsUi())

    init {
        viewModelScope.launch { manager.check() }
    }
}
