package com.tuntech.supertvstreamcast

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.tuntech.common.widget.effect.EdgeToEdgeEffect
import com.tuntech.common.widget.theme.Theme
import com.tuntech.supertvstreamcast.constant.IapConstant
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.ui.AppState
import com.tuntech.supertvstreamcast.ui.AppStateProvider
import com.tuntech.supertvstreamcast.ui.Screen
import com.tuntech.supertvstreamcast.ui.dashboard.DashboardScreen
import com.tuntech.supertvstreamcast.ui.onboarding.OnboardingScreen
import com.tuntech.supertvstreamcast.ui.paywall.Paywall2Screen
import com.tuntech.supertvstreamcast.ui.paywall.PaywallScreen
import com.tuntech.supertvstreamcast.ui.rememberAppState
import com.tuntech.supertvstreamcast.ui.splash_screen.SplashScreen
import com.tuntech.supertvstreamcast.widgets.AppLifecycleObserver

@Composable
fun MainNavHost(
    appState: AppState = rememberAppState(),
) {
    val backStack = appState.backStack ?: return

    // System bar icons contrast with the active theme: light icons at night, dark icons in daylight.
    val barTheme = if (TvColors.isDark) Theme.LIGHT else Theme.DARK
    EdgeToEdgeEffect(
        statusBarTheme = barTheme,
        navBarTheme = barTheme,
    )

    AppStateProvider(appState) {
        NavDisplay(
            backStack = backStack,
            onBack = appState::navigateBack,
            entryDecorators = listOf(
                // Required for `koinViewModel()` to scope a ViewModelStoreOwner per entry
                // (and to clear ViewModels when the entry is popped).
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Screen.Splash>(metadata = fadeMetadata()) { key ->
                    SplashScreen(
                        isInitial = key.isInitial,
                        navigateBack = appState::navigateBack,
                        navigateToDashboard = appState::replaceToDashboard,
                        navigateToOnBoarding = appState::replaceToOnboarding,
                    )
                }

                entry<Screen.Paywall>(metadata = slideUpMetadata()) { key ->
                    PaywallScreen(
                        isInitial = key.isInitial,
                        source = key.source,
                        navigateBack = {
                            appState.closePaywallWithResult(key.isPaywallClosed)
                        },
                        navigateToHome = {
                            appState.replaceToDashboard(true)
                        },
                    )
                }

                entry<Screen.Paywall2>(metadata = slideUpMetadata()) { key ->
                    Paywall2Screen(
                        isInitial = key.isInitial,
                        source = key.source,
                        navigateBack = {
                            appState.closePaywallWithResult(key.isPaywallClosed)
                        },
                        navigateToHome = {
                            appState.replaceToDashboard(true)
                        },
                    )
                }

                entry<Screen.Onboarding>(metadata = fadeMetadata()) {
                    OnboardingScreen(
                        navigateToPaywall = {
                            appState.replaceToPaywall(source = IapConstant.IAP_SOURCE_ONBOARDING)
                        },
                        navigateToDashboard = appState::replaceToDashboard,
                    )
                }

                // Stable contentKey: the result handed back from the Paywall rewrites this
                // entry's key before the paywall is popped. With the default contentKey
                // (key.toString()) that rewrite would re-create the whole Dashboard.
                entry<Screen.Dashboard>(
                    clazzContentKey = { "Dashboard" },
                    metadata = fadeMetadata(),
                ) { key ->
                    DashboardScreen(
                        isPaywallClosed = key.isPaywallClosed,
                        navigateToPaywall = { source -> appState.navigateToPaywall(false, source) },
                        navigateBack = appState::navigateBack,
                    )
                }
            },
        )
    }

    AppLifecycleObserver(
        appState = appState,
    )
}
