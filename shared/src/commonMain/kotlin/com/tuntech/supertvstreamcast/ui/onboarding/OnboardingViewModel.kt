package com.tuntech.supertvstreamcast.ui.onboarding

import androidx.lifecycle.ViewModel
import com.tuntech.supertvstreamcast.data.repository.AppSettingRepository
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.TvBrand
import com.tuntech.supertvstreamcast.platform.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val brand: TvBrand = TvBrand.SAMSUNG,
    val goal: Feature = Feature.REMOTE,
)

class OnboardingViewModel(
    private val settingsRepository: AppSettingRepository,
    private val prefs: AppPreferences,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        OnboardingUiState(
            brand = TvBrand.entries.firstOrNull { it.name == prefs.brand } ?: TvBrand.SAMSUNG,
            goal = Feature.entries.firstOrNull { it.name == prefs.goal } ?: Feature.REMOTE,
        )
    )
    val state: StateFlow<OnboardingUiState> = mutable.asStateFlow()

    fun brand(brand: TvBrand) = mutable.update { it.copy(brand = brand) }

    fun goal(goal: Feature) = mutable.update { it.copy(goal = goal) }

    /** Persists on the app scope so the write survives this screen being popped. */
    fun finishOnboarding() {
        val choices = state.value
        prefs.brand = choices.brand.name
        prefs.goal = choices.goal.name
        settingsRepository.setStartFeature(choices.goal)
        appScope.launch {
            settingsRepository.saveOnboardingSelected(true)
        }
    }
}
