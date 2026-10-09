package com.tuntech.supertvstreamcast.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import com.tuntech.supertvstreamcast.constant.IapConstant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class Screen(
    /**
     * Stable, human-readable name for analytics/logging. Each subclass passes its own literal
     * rather than relying on `this::class.simpleName`, because R8 obfuscates class names in
     * release builds. [Transient] keeps it out of the serialized back-stack form.
     */
    @Transient
    val name: String = "",
) : NavKey {

    /** Screens that must never be interrupted by an interstitial / app-open ad. */
    val skipAds: Boolean
        get() = when (this) {
            is Splash,
            is Onboarding,
            is Paywall,
            is Paywall2 -> true

            else -> false
        }

    @Serializable
    data class Paywall(
        @SerialName("isInitial")
        val isInitial: Boolean = true,
        @SerialName("source")
        val source: String = "",
    ) : Screen("Paywall") {
        val isPaywallClosed: Boolean
            get() = when (source) {
                IapConstant.IAP_SOURCE_ONBOARDING,
                IapConstant.IAP_SOURCE_HOME -> true

                else -> false
            }
    }

    /**
     * Paywall 2 — same flow as [Paywall], different plan layout. Which of the two a paywall request
     * lands on is decided by Remote Config (`IAP_SETTINGS.paywallId`) in [AppState.navigateToPaywall].
     */
    @Serializable
    data class Paywall2(
        @SerialName("isInitial")
        val isInitial: Boolean = true,
        @SerialName("source")
        val source: String = "",
    ) : Screen("Paywall2") {
        val isPaywallClosed: Boolean
            get() = when (source) {
                IapConstant.IAP_SOURCE_ONBOARDING,
                IapConstant.IAP_SOURCE_HOME -> true

                else -> false
            }
    }

    /**
     * Root screen of the app. The Paywall pushed on top of it hands its result back by rewriting
     * this key through [AppState.setPreviousResult]; `MainNavHost` pins the entry's `contentKey`
     * so that rewrite does not re-create the Dashboard.
     */
    @Serializable
    data class Dashboard(
        @SerialName("isPaywallClosed")
        val isPaywallClosed: Boolean = false,
    ) : Screen("Dashboard")

    @Serializable
    data class Splash(
        @SerialName("isInitial")
        val isInitial: Boolean = true,
    ) : Screen("Splash")

    @Serializable
    data object Onboarding : Screen("Onboarding")
}

/**
 * Explicit polymorphic serialization for the [Screen] back stack, so it can be saved/restored
 * across process death on every platform — iOS cannot rely on reflection-based serialization.
 */
@OptIn(ExperimentalSerializationApi::class)
val AppNavConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed<Screen>()
        }
    }
}

@Composable
fun rememberScreenBackStack(startDestination: NavKey = Screen.Splash()): NavBackStack<NavKey> =
    rememberNavBackStack(AppNavConfiguration, startDestination)
