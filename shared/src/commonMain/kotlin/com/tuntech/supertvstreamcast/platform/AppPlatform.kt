package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface AppPreferences {
    var onboardingDone: Boolean
    var brand: String
    var goal: String
}
@Composable expect fun rememberAppPreferences(): AppPreferences
/** Returns false if no system screen-sharing activity is available. iOS uses Control Center. */
@Composable expect fun rememberScreenSharingAction(): (() -> Boolean)?
@Composable expect fun StreamPlayer(url: String, modifier: Modifier, onError: () -> Unit)
@Composable expect fun rememberLanAccessRequest(): ((Boolean) -> Unit) -> Unit
@Composable expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
