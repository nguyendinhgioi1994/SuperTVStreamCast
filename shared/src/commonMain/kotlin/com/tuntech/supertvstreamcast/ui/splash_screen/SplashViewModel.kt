package com.tuntech.supertvstreamcast.ui.splash_screen

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import com.tuntech.common.extension.inject
import com.tuntech.common.extension.injectOrNull
import com.tuntech.common.util.PlatformContext
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.ad.AdUnitIdConfigs
import com.tuntech.monetization.iap.IapManager
import com.tuntech.monetization.util.MonetizationUtil
import com.tuntech.supertvstreamcast.constant.AdConstant
import com.tuntech.supertvstreamcast.constant.IapConstant
import com.tuntech.supertvstreamcast.data.repository.AppSettingRepository
import com.tuntech.supertvstreamcast.extension.setDefaultsAsync
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.crashlytics.crashlytics
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalAtomicApi::class)
class SplashViewModel(
    private val appSettingRepository: AppSettingRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val isInitCalled = AtomicBoolean(false)

    private val _isFinished = MutableStateFlow(false)
    val isFinished = _isFinished.asStateFlow()

    private var _isOnboardingSelected = false
    val isOnboardingSelected: Boolean
        get() = _isOnboardingSelected

    companion object {
        const val TAG: String = "SplashViewModel"
    }

    fun init(context: PlatformContext) {
        if (isInitCalled.load()) {
            return
        }
        isInitCalled.store(true)

        MonetizationUtil.startInit()

        appScope.launch(Dispatchers.IO) {
            val deferredTasks = listOf(
                async {
                    initConfig()
                },
                async {
                    initApp()
                },
            )

            deferredTasks.awaitAll()

            this.launch(Dispatchers.IO) {
                try {
                    MonetizationUtil.initAdConfigs(
                        unitIdConfigs = AdUnitIdConfigs(
                            appOpenUnitId = AdConstant.APP_OPEN_AD_UNIT_ID,
                            interstitialUnitId = AdConstant.INTERSTITIAL_AD_UNIT_ID,
                            bannerUnitId = AdConstant.BANNER_AD_UNIT_ID,
                            nativeUnitId = AdConstant.NATIVE_AD_UNIT_ID,
                            rewardedUnitId = AdConstant.REWARDED_AD_UNIT_ID,
                        ),
                    )
                    initIap(context)
                    MonetizationUtil.initAds(context)
                } catch (e: Exception) {
                    Logger.e(TAG, e) { "Failed to init monetization" }
                } finally {
                    MonetizationUtil.stopInit()
                }
                initAnalytics()
            }
        }
    }

    private suspend fun initConfig() {
        try {
            Firebase.remoteConfig.settings {
                minimumFetchInterval = 3600.toDuration(DurationUnit.SECONDS)
                fetchTimeout = 10.toDuration(DurationUnit.SECONDS)
            }
            Firebase.remoteConfig.setDefaultsAsync()
        } catch (e: Exception) {
            Logger.d(TAG, e) { "Failed to set remote config defaults" }
        }

        appScope.launch(Dispatchers.IO) {
            try {
                Firebase.remoteConfig.fetchAndActivate()

                val iapManager by injectOrNull<IapManager>(IapManager::class)
                iapManager?.updateSettings()
                val adManager by injectOrNull<AdManager>(AdManager::class)
                adManager?.updateSettings()
            } catch (e: Exception) {
                Logger.d(TAG, e) { "Failed to get remote config" }
            }
        }
    }

    private suspend fun initApp() {
        try {
            _isOnboardingSelected = appSettingRepository.isOnboardingSelected().first()
        } catch (e: Exception) {
            Logger.d(TAG, e) { "Failed to load cache settings" }
        }

        _isFinished.value = true
    }

    /** Without a Qonversion project key there is no store to talk to: entitlement stays free. */
    private suspend fun initIap(context: PlatformContext) {
        if (IapConstant.PROJECT_KEY.isBlank()) {
            Logger.w(TAG) { "Skipping IAP init: no Qonversion project key configured." }
            return
        }
        // The module's initIap also warms the IAP discount campaign's media
        // (IapManager.preloadAssets) so the win-back modal renders instantly on paywall close.
        MonetizationUtil.initIap(
            context = context,
            entitlementKey = IapConstant.ENTITLEMENT_KEY,
            projectKey = IapConstant.PROJECT_KEY,
        )
    }

    private suspend fun initAnalytics() {
        try {
            val iapManager by inject<IapManager>(IapManager::class)

            iapManager.getUserInfo()?.let { userInfo ->
                Firebase.analytics.setUserProperty("qon_id", userInfo.qonversionId)
                Firebase.crashlytics.setCustomKey("qon_id", userInfo.qonversionId)
            }
        } catch (e: Exception) {
            Logger.d(TAG, e) { "Failed to init analytics" }
        }
    }
}
