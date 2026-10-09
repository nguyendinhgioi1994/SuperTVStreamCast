package com.tuntech.supertvstreamcast.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.theme.LocalReducedMotion
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvMotion
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay

private val Still = mutableStateOf(0f)

/** A value travelling 0 → 1 → 0 forever; stays at 0 when [enabled] is false or motion is reduced. */
@Composable internal fun rememberPulse(durationMillis: Int = 2400, enabled: Boolean = true): State<Float> {
    if (!enabled || LocalReducedMotion.current) return Still
    return rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f,
        infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulseValue")
}

/** A value sweeping 0 → 1 and restarting; stays at 0 when [enabled] is false or motion is reduced. */
@Composable internal fun rememberSweep(durationMillis: Int = 2600, enabled: Boolean = true): State<Float> {
    if (!enabled || LocalReducedMotion.current) return Still
    return rememberInfiniteTransition(label = "sweep").animateFloat(0f, 1f,
        infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart), label = "sweepValue")
}

/** Controls dip slightly while held and spring back. */
@Composable internal fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.94f): Modifier {
    val reduced = LocalReducedMotion.current
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && !reduced) pressed else 1f, TvMotion.press(), label = "pressScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Content fades and rises into place once; [index] staggers neighbours. */
@Composable internal fun Reveal(index: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index.toLong() * TvMotion.Stagger)
            progress.animateTo(1f, tween(TvMotion.Screen, easing = TvMotion.Emphasized))
        }
    }
    Box(modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 24.dp.toPx() }) { content() }
}

/** A light band travelling across the gradient of a call-to-action. */
@Composable internal fun Modifier.shimmer(enabled: Boolean = true): Modifier {
    val sweep = rememberSweep(3200, enabled)
    if (!enabled || LocalReducedMotion.current) return this
    return drawWithContent {
        drawContent()
        // The band crosses in the first 40% of the cycle, then rests.
        val t = (sweep.value / 0.4f).coerceAtMost(1f)
        if (t < 1f) {
            val band = size.width * 0.35f
            val x = -band + (size.width + 2 * band) * t
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                start = Offset(x - band, 0f), end = Offset(x + band, size.height)))
        }
    }
}

/** The page wash behind every screen: theme gradient, slow drifting light and, at night, a few stars. */
@Composable internal fun Modifier.tvBackdrop(): Modifier {
    val drift = rememberPulse(16000)
    val dark = TvColors.isDark
    val cyan = TvColors.Cyan; val violet = TvColors.Pink; val coral = TvColors.Amber; val star = TvColors.Text
    return background(TvColors.Background).background(TvColors.BackgroundGradient).drawBehind {
        val t = drift.value
        val w = size.width; val h = size.height
        fun orb(color: Color, x: Float, y: Float, radius: Float, strength: Float) {
            val center = Offset(x, y)
            drawCircle(TvColors.glow(color, center, radius, strength), radius, center)
        }
        orb(cyan, w * (0.1f + 0.12f * t), h * 0.06f, w * 0.8f, 0.9f)
        orb(violet, w * (1f - 0.1f * t), h * (0.34f + 0.05f * t), w * 0.65f, 0.7f)
        orb(coral, w * (0.12f + 0.08f * t), h * (0.98f - 0.05f * t), w * 0.6f, 0.5f)
        if (dark) Stars.forEachIndexed { i, (sx, sy) ->
            val twinkle = 0.25f + 0.35f * if (i % 2 == 0) t else 1f - t
            drawCircle(star.copy(alpha = twinkle * 0.5f), (0.6f + (i % 3) * 0.35f).dp.toPx(), Offset(sx * w, sy * h))
        }
    }
}
private val Stars = listOf(0.08f to 0.11f, 0.22f to 0.05f, 0.37f to 0.16f, 0.55f to 0.07f, 0.71f to 0.13f, 0.9f to 0.06f, 0.82f to 0.24f,
    0.15f to 0.29f, 0.46f to 0.31f, 0.64f to 0.42f, 0.05f to 0.52f, 0.93f to 0.56f, 0.3f to 0.63f, 0.76f to 0.71f, 0.12f to 0.83f, 0.58f to 0.88f)

/** Tabs cross-fade with a short rise; reduced motion keeps only a quick fade. */
internal fun tabTransition(reduced: Boolean): androidx.compose.animation.ContentTransform =
    if (reduced) androidx.compose.animation.fadeIn(tween(100)) togetherWith androidx.compose.animation.fadeOut(tween(100))
    else (androidx.compose.animation.fadeIn(tween(TvMotion.Screen, easing = TvMotion.Emphasized)) +
        androidx.compose.animation.slideInVertically(tween(TvMotion.Screen, easing = TvMotion.Emphasized)) { it / 24 }) togetherWith
        androidx.compose.animation.fadeOut(tween(TvMotion.Micro, easing = TvMotion.Exit))
