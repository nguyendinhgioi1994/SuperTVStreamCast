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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.tuntech.supertvstreamcast.theme.LocalReducedMotion
import com.tuntech.supertvstreamcast.theme.TvMotion
import com.tuntech.supertvstreamcast.ui.app_loading.AppIconLoader
import shared.resources.tagline
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.widgets.AppBackHandler
import com.tuntech.supertvstreamcast.widgets.CommonAppBackground
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import com.tuntech.monetization.ad.AdManager
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
    navigateToLanguage: () -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
    adManager: AdManager = koinInject(),
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
            navigateToLanguage()
            return@LaunchedEffect
        }

        if (isInitial) adManager.showSplashAd(context)
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
        // The icon settles in once while its ring spins; nothing here delays leaving the splash.
        val reduced = LocalReducedMotion.current
        val enter = remember { Animatable(if (reduced) 1f else 0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(TvMotion.Splash, easing = TvMotion.Emphasized)) }
        Column(
            modifier = Modifier.padding(horizontal = 32.dp).graphicsLayer {
                alpha = enter.value
                val scale = 0.92f + 0.08f * enter.value
                scaleX = scale; scaleY = scale
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppIconLoader()
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
