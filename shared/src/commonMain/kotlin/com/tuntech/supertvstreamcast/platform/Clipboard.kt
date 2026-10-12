package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable

/** Puts plain text on the system clipboard. */
@Composable expect fun rememberCopyText(): (String) -> Unit
