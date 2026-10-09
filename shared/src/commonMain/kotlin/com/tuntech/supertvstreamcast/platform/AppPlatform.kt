package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Returns false if no system screen-sharing activity is available. iOS uses Control Center. */
@Composable expect fun rememberScreenSharingAction(): (() -> Boolean)?
/**
 * Native video player with its own transport controls. [userAgent] / [referer] are sent with the
 * stream requests when not empty. A video on demand starts at [startMs] and reports its position
 * and duration (0 when unknown, e.g. live) through [onProgress] every few seconds and when it is
 * closed; [onEnded] is called when it reaches its end. [onError] is called once playback cannot continue.
 * [controls] hides the transport controls for the mini player and picture-in-picture; [fill] crops
 * the picture to fill the view instead of letterboxing it.
 */
@Composable expect fun StreamPlayer(url: String, userAgent: String, referer: String, modifier: Modifier, startMs: Long, controls: Boolean, fill: Boolean,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit, onEnded: () -> Unit, onError: () -> Unit)
/**
 * Turns the screen to landscape without the system bars (true) and back (false) for a phone that
 * is otherwise held to portrait; everything is restored when the caller leaves. Null where the
 * device rotates on its own, as on iOS.
 */
@Composable expect fun rememberFullscreenRequest(): ((Boolean) -> Unit)?
@Composable expect fun rememberLanAccessRequest(): ((Boolean) -> Unit) -> Unit
/**
 * System document picker. [onPicked] receives at most [maxBytes] + 1 bytes (so callers can reject
 * oversize files) or null if the file could not be read; it is not called when the user cancels.
 */
@Composable expect fun rememberFilePicker(maxBytes: Int, onPicked: (ByteArray?) -> Unit): () -> Unit
