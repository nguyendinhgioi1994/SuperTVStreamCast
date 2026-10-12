package com.tuntech.supertvstreamcast.ui.app_loading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.common.widget.dialog.LoadingContent
import com.tuntech.common.widget.dialog.ModalContent
import com.tuntech.common.widget.dialog.ModalStack
import com.tuntech.supertvstreamcast.theme.LocalReducedMotion
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvDimens
import com.tuntech.supertvstreamcast.theme.TvMotion
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.Glyph
import com.tuntech.supertvstreamcast.ui.GlyphIcon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import shared.resources.Res
import shared.resources.ad_loading
import shared.resources.app_icon
import shared.resources.close
import shared.resources.please_wait_a_moment


/** The app's single blocking loading. With a timeout the close button unlocks after the countdown. */
@Composable
fun AppLoadingContent(
    title: String,
    description: String,
    isTimeoutEnabled: Boolean = true,
    onClose: () -> Unit = {},
    viewModel: AppLoadingViewModel = koinViewModel(),
) {
    val remainingTime by viewModel.remainingTime.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        if (isTimeoutEnabled) {
            viewModel.start()
        }
        onDispose {
            viewModel.stop()
        }
    }

    AppLoadingBody(
        title = title,
        description = description,
        remainingSeconds = if (isTimeoutEnabled) remainingTime / 1000 else null,
        onClose = onClose,
    )
}

/** [remainingSeconds] null hides the close button; 0 enables it. */
@Composable
private fun AppLoadingBody(
    title: String,
    description: String,
    remainingSeconds: Long?,
    onClose: () -> Unit = {},
) {
    val reduced = LocalReducedMotion.current
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(TvMotion.Modal, easing = TvMotion.Emphasized)) }

    Box(
        modifier = Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter.value }
            .background(Color.Black.copy(alpha = 0.6f))
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier.padding(TvDimens.SpaceLg).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIconLoader(
                modifier = Modifier.graphicsLayer {
                    val scale = 0.82f + 0.18f * enter.value
                    scaleX = scale; scaleY = scale
                },
            )
            Spacer(Modifier.height(TvDimens.SpaceXl))
            Text(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    translationY = (1f - enter.value) * 24.dp.toPx()
                },
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TvDimens.SpaceSm))
            Text(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    translationY = (1f - enter.value) * 36.dp.toPx()
                },
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TvDimens.SpaceLg))
            LoadingDots()
        }

        if (remainingSeconds != null) {
            CloseCountdownButton(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                remainingSeconds = remainingSeconds,
                onClose = onClose,
            )
        }
    }
}

/** The app icon inside a spinning brand-gradient ring with a breathing halo. */
@Composable
internal fun AppIconLoader(modifier: Modifier = Modifier, iconSize: Dp = 112.dp) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "appIconLoader")
    val rotation by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring",
    )
    val sweep by transition.animateFloat(
        70f, 250f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sweep",
    )
    val breath by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath",
    )
    val ringColors = listOf(TvColors.Violet, TvColors.Pink, TvColors.Amber, TvColors.Violet)
    val glow = TvColors.Pink
    val track = Color.White.copy(alpha = 0.18f)
    val ringStroke = 4.dp
    val ringGap = 14.dp

    Box(
        modifier = modifier.size(iconSize + (ringGap + ringStroke) * 2).drawBehind {
            val halo = if (reduced) 0.5f else breath
            val radius = size.minDimension * (0.78f + 0.22f * halo)
            drawCircle(TvColors.glow(glow, center, radius, 1.4f + 0.8f * halo), radius)

            val stroke = ringStroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            rotate(if (reduced) 0f else rotation) {
                drawArc(
                    brush = Brush.sweepGradient(ringColors, center),
                    startAngle = 0f,
                    sweepAngle = if (reduced) 360f else sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize).graphicsLayer {
                val scale = if (reduced) 1f else 0.96f + 0.04f * breath
                scaleX = scale; scaleY = scale
            }.shadow(18.dp, RoundedCornerShape(TvDimens.Radius), ambientColor = glow, spotColor = glow)
                .clip(RoundedCornerShape(TvDimens.Radius)),
        )
    }
}

/** Three dots in the brand colours rising one after another. */
@Composable
private fun LoadingDots() {
    if (LocalReducedMotion.current) return
    val transition = rememberInfiniteTransition(label = "dots")
    val colors = listOf(TvColors.Violet, TvColors.Pink, TvColors.Amber)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { index, color ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 1200
                        0f at 0
                        1f at 300 using FastOutSlowInEasing
                        0f at 600
                    },
                    initialStartOffset = StartOffset(index * 150),
                ),
                label = "dot$index",
            )
            Box(
                Modifier.size(9.dp).graphicsLayer {
                    translationY = -8.dp.toPx() * phase
                    alpha = 0.45f + 0.55f * phase
                }.background(color, CircleShape)
            )
        }
    }
}

/** Shows the seconds left inside a draining ring; becomes a close button at 0. */
@Composable
private fun CloseCountdownButton(modifier: Modifier, remainingSeconds: Long, onClose: () -> Unit) {
    val closeLabel = stringResource(Res.string.close)
    val total = AppLoadingViewModel.TIME_OUT / 1000f
    val progress by animateFloatAsState(
        targetValue = (remainingSeconds / total).coerceIn(0f, 1f),
        animationSpec = tween(AppLoadingViewModel.INTERVAL.toInt(), easing = LinearEasing),
        label = "countdown",
    )
    val ring = TvColors.Pink
    IconButton(
        modifier = modifier.size(48.dp)
            .semantics { contentDescription = closeLabel }
            .drawBehind {
                if (remainingSeconds > 0) {
                    val stroke = 2.5.dp.toPx()
                    drawArc(
                        ring, -90f, 360f * progress, false,
                        Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = TvColors.Raised,
            disabledContainerColor = TvColors.Raised.copy(alpha = 0.6f),
        ),
        onClick = onClose,
        enabled = remainingSeconds <= 0,
    ) {
        AnimatedContent(
            targetState = remainingSeconds > 0,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() },
            label = "closeContent",
        ) { counting ->
            if (counting) {
                Text(
                    text = "$remainingSeconds",
                    style = MaterialTheme.typography.labelLarge,
                    color = TvColors.Muted,
                )
            } else {
                GlyphIcon(Glyph.CLOSE, Modifier.size(18.dp), TvColors.Text)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun PreviewAppLoadingContent() {
    TvTheme { AppLoadingBody(title = "Purchasing …", description = "Please wait a moment", remainingSeconds = 12) }
}

fun ModalStack.showAppLoading(
    title: StringResource,
    description: StringResource,
    isTimeoutEnabled: Boolean = true,
    onClose: () -> Unit = {},
) {
    show(
        ModalContent(
            id = ModalContent.uuid,
            loading = LoadingContent {
                AppLoadingContent(
                    title = stringResource(title),
                    description = stringResource(description),
                    isTimeoutEnabled = isTimeoutEnabled,
                    onClose = {
                        onClose()
                        dismissLoading()
                    },
                )
            }
        )
    )
}

fun ModalStack.showAdLoading(
    onClose: (() -> Unit)? = null,
) {
    show(
        ModalContent(
            id = ModalContent.uuid,
            loading = LoadingContent {
                AppLoadingContent(
                    title = stringResource(Res.string.ad_loading),
                    description = stringResource(Res.string.please_wait_a_moment),
                    onClose = {
                        onClose?.invoke()
                        dismissLoading()
                    },
                )
            }
        )
    )
}
