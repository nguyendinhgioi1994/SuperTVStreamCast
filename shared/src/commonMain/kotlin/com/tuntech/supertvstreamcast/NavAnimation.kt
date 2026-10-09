package com.tuntech.supertvstreamcast

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.metadata
import com.tuntech.supertvstreamcast.theme.TvMotion
import androidx.navigation3.ui.NavDisplay

const val NAV_ANIM_DURATION = TvMotion.Screen

/**
 * Per-entry fade override. Screens without metadata keep [NavDisplay]'s built-in
 * platform-default animation; only entries that opt in via this metadata fade.
 * Used for Splash / Onboarding / Dashboard.
 */
fun fadeMetadata(): Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) {
        fadeIn(animationSpec = tween(NAV_ANIM_DURATION, easing = TvMotion.Standard)) togetherWith
            fadeOut(animationSpec = tween(NAV_ANIM_DURATION, easing = TvMotion.Standard))
    }
    put(NavDisplay.PopTransitionKey) {
        fadeIn(animationSpec = tween(NAV_ANIM_DURATION, easing = TvMotion.Standard)) togetherWith
            fadeOut(animationSpec = tween(NAV_ANIM_DURATION, easing = TvMotion.Standard))
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        fadeIn(animationSpec = tween(NAV_ANIM_DURATION, easing = TvMotion.Standard)) togetherWith
            fadeOut(animationSpec = tween(NAV_ANIM_DURATION, easing = TvMotion.Standard))
    }
}

/**
 * Per-entry animation for modal-style screens (Paywall): slide up over the previous
 * screen on enter, slide back down on pop. [ExitTransition.KeepUntilTransitionsFinished]
 * keeps the underlying screen visible.
 */
fun slideUpMetadata(): Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) {
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(TvMotion.Modal, easing = TvMotion.Emphasized),
        ) togetherWith ExitTransition.KeepUntilTransitionsFinished
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(TvMotion.Modal, easing = TvMotion.Emphasized),
        )
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(TvMotion.Modal, easing = TvMotion.Emphasized),
        )
    }
}
