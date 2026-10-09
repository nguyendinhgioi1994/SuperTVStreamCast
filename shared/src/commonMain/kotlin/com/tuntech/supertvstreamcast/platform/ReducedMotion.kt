package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable

/** The system accessibility setting that asks apps to drop non-essential animation. */
@Composable expect fun rememberReducedMotion(): Boolean
