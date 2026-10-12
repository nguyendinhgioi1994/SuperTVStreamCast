package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.ui.onboarding.OnboardingPage
import com.tuntech.supertvstreamcast.ui.onboarding.onboardingPages
import com.tuntech.supertvstreamcast.ui.onboarding.stepAt
import kotlin.test.*

class PremiumTest {
    @Test fun settingsKeepOnlyKnownRemoteFeaturesAndFallBackSafely() {
        val settings = parsePremiumSettings("""{"lockedFeatures":["REMOTE_KEYBOARD","REMOTE_APPS","IPTV_SOURCES","NOPE"],"rewardUnlockMinutes":"15","rewardSources":-2}""")
        assertEquals(setOf(PremiumFeature.REMOTE_KEYBOARD, PremiumFeature.REMOTE_APPS), settings.locked)
        assertEquals(15, settings.rewardUnlockMinutes)
        assertEquals(0, settings.rewardSources)
        assertEquals(PremiumSettings().rewardWatchSeconds, settings.rewardWatchSeconds)
        assertEquals(PremiumSettings(), parsePremiumSettings("not json"))
        assertTrue(parsePremiumSettings("{}").locked.isEmpty())
    }

    @Test fun rewardUnlocksOneFeatureUntilItsDeadline() {
        val settings = PremiumSettings(locked = setOf(PremiumFeature.REMOTE_KEYBOARD, PremiumFeature.REMOTE_TOUCHPAD))
        val now = 1_000L
        val unlocks = FeatureUnlocks().unlock(PremiumFeature.REMOTE_KEYBOARD, now, minutes = 30)
        assertEquals(setOf(PremiumFeature.REMOTE_TOUCHPAD), unlocks.locked(settings, premium = false, now = now))
        assertFalse(unlocks.isLocked(PremiumFeature.REMOTE_APPS, settings, premium = false, now = now))
        assertEquals(now + 1_800, unlocks.nextExpiry(now))
        assertTrue(unlocks.isLocked(PremiumFeature.REMOTE_KEYBOARD, settings, premium = false, now = now + 1_800))
        assertNull(unlocks.nextExpiry(now + 1_800))
        assertTrue(FeatureUnlocks().locked(settings, premium = true, now = now).isEmpty())
    }

    @Test fun rewardedSourceSlotsRaiseTheFreeLimit() {
        val limits = IptvLimits(maxSources = 1)
        assertFalse(limits.canAddSource(1, premium = false))
        assertTrue(limits.canAddSource(1, premium = false, extraSources = 1))
        assertFalse(limits.canAddSource(2, premium = false, extraSources = 1))
    }

    @Test fun onboardingAdsSitAfterTheirContentPage() {
        val content = (0 until 3).map { OnboardingPage.Content(it) }
        assertEquals(content, onboardingPages(3, emptyList()))
        val pages = onboardingPages(3, listOf("A" to 2, "B" to 9, "C" to 0))
        assertEquals(listOf(content[0], OnboardingPage.Ad("C"), content[1], OnboardingPage.Ad("A"), content[2], OnboardingPage.Ad("B")), pages)
        assertEquals(listOf(0, 0, 1, 1, 2, 2), pages.indices.map { pages.stepAt(it) })
    }
}
