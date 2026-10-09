package com.tuntech.supertvstreamcast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.common.util.AppEnvironment
import com.tuntech.common.util.AppLifecycleMonitorProvider
import com.tuntech.common.util.AppRelaunchEffect
import com.tuntech.common.widget.dialog.LocalModalContext
import com.tuntech.common.widget.dialog.ModalController
import com.tuntech.monetization.util.MonetizationUtil
import com.tuntech.supertvstreamcast.theme.TvAdTheme
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.AppState
import com.tuntech.supertvstreamcast.ui.Screen
import com.tuntech.supertvstreamcast.ui.rememberAppState

@Composable
fun MainApp(
    appState: AppState = rememberAppState(),
    isRestored: Boolean = false,
) {
    AppLifecycleMonitorProvider {
        // A process restored without its monetization state goes through the Splash again.
        AppRelaunchEffect(
            isRestored = isRestored,
            isReady = { MonetizationUtil.isReady },
            showSplash = {
                val backStack = appState.backStack
                if (backStack != null && backStack.lastOrNull() !is Screen.Splash) {
                    backStack.add(Screen.Splash(isInitial = false))
                }
            },
        )
        AppEnvironment {
            val themeMode by org.koin.compose.koinInject<com.tuntech.supertvstreamcast.data.repository.ThemeRepository>().mode.collectAsStateWithLifecycle()
            TvTheme(themeMode) {
                TvAdTheme {
                    ModalController {
                        appState.modalContext = LocalModalContext.current

                        MainNavHost(
                            appState = appState,
                        )
                    }
                }
            }
        }
    }
}
