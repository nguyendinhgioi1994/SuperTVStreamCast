package com.tuntech.supertvstreamcast.ui.splash_screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.common.util.LocalPlatformContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.tuntech.supertvstreamcast.theme.LocalReducedMotion
import com.tuntech.supertvstreamcast.theme.TvMotion
import com.tuntech.supertvstreamcast.ui.BrandMark
import com.tuntech.supertvstreamcast.ui.rememberPulse
import shared.resources.tagline
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.widgets.AppBackHandler
import com.tuntech.supertvstreamcast.widgets.CommonAppBackground
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import shared.resources.Res
import shared.resources.app_name

/**
 * Loads the stored onboarding flag while monetization starts in the background; it leaves as soon
 * as the flag is known (no artificial delay).
 */
@Composable
fun SplashScreen(
    isInitial: Boolean = true,
    navigateBack: () -> Unit,
    navigateToDashboard: () -> Unit,
    navigateToOnBoarding: () -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val context = LocalPlatformContext.current
    val isFinished by viewModel.isFinished.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    fun close() {
        if (!isInitial) {
            navigateBack()
            return
        }
        navigateToDashboard()
    }
    AppBackHandler(enabled = false) {}

    LaunchedEffect(isFinished) {
        if (!isFinished) {
            return@LaunchedEffect
        }

        if (!viewModel.isOnboardingSelected) {
            navigateToOnBoarding()
            return@LaunchedEffect
        }

        close()
    }

    SplashContent()
}

@Composable
private fun SplashContent() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CommonAppBackground()
        // The mark settles in once and its halo breathes; nothing here delays leaving the splash.
        val reduced = LocalReducedMotion.current
        val enter = remember { Animatable(if (reduced) 1f else 0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(TvMotion.Splash, easing = TvMotion.Emphasized)) }
        val halo = rememberPulse(1800)
        val glow = TvColors.Cyan
        Column(
            modifier = Modifier.padding(horizontal = 32.dp).graphicsLayer {
                alpha = enter.value
                val scale = 0.92f + 0.08f * enter.value
                scaleX = scale; scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandMark(
                Modifier.size(104.dp).drawBehind {
                    val radius = size.minDimension * (0.95f + 0.4f * halo.value)
                    drawCircle(TvColors.glow(glow, center, radius, 1.5f), radius)
                }
            )
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = TvColors.Text,
            )
            Text(
                text = stringResource(Res.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun SplashPreview() {
    TvTheme { SplashContent() }
}
