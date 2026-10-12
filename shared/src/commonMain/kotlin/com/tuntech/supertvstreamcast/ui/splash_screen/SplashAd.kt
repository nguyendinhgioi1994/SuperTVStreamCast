package com.tuntech.supertvstreamcast.ui.splash_screen

import com.tuntech.common.util.PlatformContext
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.ad.AdManagerCallback
import com.tuntech.monetization.iap.IapManager
import com.tuntech.supertvstreamcast.constant.AdName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Longest wait for the ads SDK and the entitlement check before giving up on the splash ad. */
private const val SPLASH_AD_WAIT_MS = 3_000L
/** Longest wait for the ad request when nothing was preloaded. */
private const val SPLASH_AD_LOAD_MS = 4_000L

/**
 * Cold-start app-open ad for a returning user ([AdName.SPLASH]). Returns once the ad is closed or
 * skipped: premium users, ads turned off in Remote Config, or the SDK/ad not ready in time.
 */
internal suspend fun AdManager.showSplashAd(context: PlatformContext) {
    // initAds activates the managers only after the entitlement check, so wait for one of the two outcomes.
    val go = withTimeoutOrNull(SPLASH_AD_WAIT_MS) {
        var decision = splashAdDecision()
        while (decision == null) { delay(100); decision = splashAdDecision() }
        decision
    } ?: false
    if (!go) return

    val closed = CompletableDeferred<Unit>()
    val shown = CompletableDeferred<Unit>()
    var cancelLoading: (() -> Unit)? = null
    showAppOpenAd(context = context, name = AdName.SPLASH, callback = AdManagerCallback(
        onAdLoading = { cancel -> cancelLoading = cancel },
        onAdShowedFullScreenContent = { shown.complete(Unit) },
        onAdFailedToLoad = { closed.complete(Unit) },
        onAdClosed = { closed.complete(Unit) },
    ))
    // A slow request is dropped instead of holding the user on the splash; a shown ad runs until closed.
    if (withTimeoutOrNull(SPLASH_AD_LOAD_MS) { shown.await() } == null && !closed.isCompleted) {
        cancelLoading?.invoke()
        closed.complete(Unit)
    }
    closed.await()
}

/** True to show, false to skip, null while the SDK or the entitlement check is still starting. */
private fun AdManager.splashAdDecision(): Boolean? {
    if (IapManager.shared.isActive.value) return false
    val manager = managers[AdManager.APP_OPEN_AD] ?: return null
    if (!isReady.value) return null
    if (!manager.settings.isEnabled || !manager.getAdSetting(AdName.SPLASH).isEnabled) return false
    return if (manager.isEnabled.value) true else null
}
