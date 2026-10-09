package com.tuntech.supertvstreamcast

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.navigation3.runtime.NavKey
import com.tuntech.supertvstreamcast.constant.IapConstant
import com.tuntech.supertvstreamcast.data.repository.AppSettingRepository
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.TvBrand
import com.tuntech.supertvstreamcast.platform.AppPreferences
import com.tuntech.supertvstreamcast.ui.AppNavConfiguration
import com.tuntech.supertvstreamcast.ui.Screen
import com.tuntech.supertvstreamcast.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private class MemoryPreferences : AppPreferences {
    override var brand = ""
    override var goal = ""
    override var lastDevice = ""
}

class EntryFlowTest {
    @Test fun entryScreensNeverShowAds() {
        listOf(Screen.Splash(), Screen.Onboarding, Screen.Paywall(), Screen.Paywall2()).forEach { assertTrue(it.skipAds, it.name) }
        assertFalse(Screen.Dashboard().skipAds)
    }

    @Test fun onlyStartupPaywallsReportTheirDismissal() {
        assertTrue(Screen.Paywall(source = IapConstant.IAP_SOURCE_ONBOARDING).isPaywallClosed)
        assertTrue(Screen.Paywall2(source = IapConstant.IAP_SOURCE_HOME).isPaywallClosed)
        assertFalse(Screen.Paywall(source = IapConstant.IAP_SOURCE_SETTING).isPaywallClosed)
    }

    @Test fun backStackKeysSurviveSerialization() {
        val json = Json { serializersModule = AppNavConfiguration.serializersModule }
        val serializer = PolymorphicSerializer(NavKey::class)
        listOf(Screen.Splash(isInitial = false), Screen.Onboarding, Screen.Dashboard(isPaywallClosed = true),
            Screen.Paywall(isInitial = false, source = IapConstant.IAP_SOURCE_SETTING)).forEach { screen ->
            assertEquals<NavKey>(screen, json.decodeFromString(serializer, json.encodeToString(serializer, screen)))
        }
    }

    @Test fun onboardingIsUnfinishedUntilSaved() = runTest {
        val settings = AppSettingRepository(MemoryDataStore())
        assertFalse(settings.isOnboardingSelected().first())
        settings.saveOnboardingSelected(true)
        assertTrue(settings.isOnboardingSelected().first())
    }

    @Test fun finishingOnboardingStoresChoicesAndOpensGoalOnce() = runTest {
        val settings = AppSettingRepository(MemoryDataStore())
        val prefs = MemoryPreferences()
        val model = OnboardingViewModel(settings, prefs, this)
        model.brand(TvBrand.LG)
        model.goal(Feature.IPTV)
        model.finishOnboarding()
        testScheduler.advanceUntilIdle()

        assertEquals("LG", prefs.brand)
        assertEquals("IPTV", prefs.goal)
        assertTrue(settings.isOnboardingSelected().first())
        assertEquals(Feature.IPTV, settings.consumeStartFeature())
        assertEquals(Feature.HOME, settings.consumeStartFeature())
    }

    @Test fun onboardingStartsFromStoredChoices() {
        val prefs = MemoryPreferences().apply { brand = "SONY"; goal = "MIRROR" }
        val state = OnboardingViewModel(AppSettingRepository(MemoryDataStore()), prefs, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)).state.value
        assertEquals(TvBrand.SONY, state.brand)
        assertEquals(Feature.MIRROR, state.goal)
    }
}
