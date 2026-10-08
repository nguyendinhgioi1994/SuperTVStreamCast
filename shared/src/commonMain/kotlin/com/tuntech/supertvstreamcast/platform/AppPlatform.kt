package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable expect fun rememberAppPreferences(): AppPreferences
/** Returns false if no system screen-sharing activity is available. iOS uses Control Center. */
@Composable expect fun rememberScreenSharingAction(): (() -> Boolean)?
@Composable expect fun StreamPlayer(url: String, modifier: Modifier, onError: () -> Unit)
@Composable expect fun rememberLanAccessRequest(): ((Boolean) -> Unit) -> Unit
@Composable expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
/**
 * System document picker. [onPicked] receives at most [maxBytes] + 1 bytes (so callers can reject
 * oversize files) or null if the file could not be read; it is not called when the user cancels.
 */
@Composable expect fun rememberFilePicker(maxBytes: Int, onPicked: (ByteArray?) -> Unit): () -> Unit
