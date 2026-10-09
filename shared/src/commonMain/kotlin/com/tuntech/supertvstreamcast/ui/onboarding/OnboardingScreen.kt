package com.tuntech.supertvstreamcast.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.common.util.LocalPlatformContext
import com.tuntech.common.widget.dialog.LocalModalContext
import com.tuntech.mmp.MmpManager
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.ad.AdManagerCallback
import com.tuntech.monetization.ad.widget.NativeAd
import com.tuntech.monetization.ad.widget.rememberAdState
import com.tuntech.monetization.iap.IapManager
import com.tuntech.monetization.util.MonetizationUtil
import com.tuntech.supertvstreamcast.constant.AdName
import com.tuntech.supertvstreamcast.constant.AnalyticsEvent
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.TvBrand
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvDimens
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.Artwork
import com.tuntech.supertvstreamcast.ui.BrandGrid
import com.tuntech.supertvstreamcast.ui.Heading
import com.tuntech.supertvstreamcast.ui.IconBubble
import com.tuntech.supertvstreamcast.ui.PrimaryCta
import com.tuntech.supertvstreamcast.ui.TvArt
import com.tuntech.supertvstreamcast.theme.TvMotion
import com.tuntech.supertvstreamcast.ui.app_loading.showAdLoading
import com.tuntech.supertvstreamcast.ui.label
import com.tuntech.supertvstreamcast.ui.navLabel
import com.tuntech.supertvstreamcast.widgets.AppBackHandler
import com.tuntech.supertvstreamcast.widgets.BackHandlerType
import com.tuntech.supertvstreamcast.widgets.CommonAppBackground
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import shared.resources.Res
import shared.resources.app_name
import shared.resources.back
import shared.resources.brand_body
import shared.resources.brand_title
import shared.resources.continue_label
import shared.resources.get_started
import shared.resources.goal_body
import shared.resources.goal_title
import shared.resources.onboarding_step
import shared.resources.welcome_body
import shared.resources.welcome_title

private const val PAGE_COUNT = 3

/**
 * First-launch flow: introduction → TV brand → first goal. Finishing it shows the introduction
 * interstitial, stores the choices, then opens the first-start paywall (when Remote Config enables
 * it and the user is not premium) or the Dashboard.
 */
@Composable
fun OnboardingScreen(
    navigateToPaywall: () -> Unit = {},
    navigateToDashboard: () -> Unit = {},
    viewModel: OnboardingViewModel = koinViewModel(),
    adManager: AdManager = koinInject(),
    iapManager: IapManager = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val modalContext = LocalModalContext.current
    val adState = rememberAdState()
    val pagerState = rememberPagerState(
        pageCount = { PAGE_COUNT },
    )

    fun close() {
        adManager.showInterstitialAd(
            name = AdName.INTRODUCTION,
            context = context,
            callback = AdManagerCallback(
                onAdLoading = modalContext::showAdLoading,
                onAdShowedFullScreenContent = modalContext::dismissLoading,
                onAdClosed = {
                    modalContext.dismissLoading()

                    viewModel.finishOnboarding()

                    // No paywall before the store is initialised: it would have no real product.
                    if (MonetizationUtil.isIapReady && !iapManager.isActive.value && iapManager.isFirstStartupEnabled) {
                        iapManager.isFirstStartupStarted = true
                        navigateToPaywall()
                        return@AdManagerCallback
                    }

                    navigateToDashboard()
                },
            )
        )

        MmpManager.shared?.trackEvent(
            event = AnalyticsEvent.COMPLETE_ONBOARDING,
        )
    }

    fun handleContinue() {
        if (pagerState.currentPage < pagerState.pageCount - 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(page = pagerState.currentPage + 1)
            }
            return
        }

        close()
    }

    fun handleBack() {
        coroutineScope.launch {
            pagerState.animateScrollToPage(page = pagerState.currentPage - 1)
        }
    }

    AppBackHandler(
        type = BackHandlerType.NONE,
        enabled = pagerState.currentPage > 0,
        onBack = ::handleBack,
    )

    LaunchedEffect(pagerState.currentPage) {
        adState.reload()
    }

    OnboardingScaffold(
        state = state,
        pagerState = pagerState,
        onNext = ::handleContinue,
        onBack = ::handleBack,
        onBrand = viewModel::brand,
        onGoal = viewModel::goal,
        ad = {
            NativeAd(
                name = AdName.INTRODUCTION,
                state = adState,
            )
        },
    )
}

@Composable
internal fun OnboardingScaffold(
    state: OnboardingUiState,
    pagerState: PagerState,
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    onBrand: (TvBrand) -> Unit = {},
    onGoal: (Feature) -> Unit = {},
    ad: @Composable () -> Unit = {},
) {
    val page = pagerState.currentPage
    Box(Modifier.fillMaxSize()) {
        CommonAppBackground()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(TvDimens.Space),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = TvColors.Cyan,
                )
                Text(
                    stringResource(Res.string.onboarding_step, page + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = TvColors.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PAGE_COUNT) { step ->
                    val fill by animateFloatAsState(if (step <= page) 1f else 0f, tween(TvMotion.Card, easing = TvMotion.Standard), label = "onboardingStep")
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(TvColors.Outline)) {
                        Box(Modifier.fillMaxWidth(fill).height(4.dp).clip(CircleShape).background(TvColors.Gradient))
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = TvDimens.Space,
                verticalAlignment = Alignment.Top,
            ) { index ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    when (index) {
                        0 -> IntroductionPage()
                        1 -> {
                            Heading(stringResource(Res.string.brand_title), stringResource(Res.string.brand_body))
                            BrandGrid(state.brand, onBrand)
                        }

                        else -> GoalPage(state.goal, onGoal)
                    }
                }
            }
            PrimaryCta(
                stringResource(if (page == PAGE_COUNT - 1) Res.string.get_started else Res.string.continue_label),
                onClick = onNext,
            )
            if (page > 0) {
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(Res.string.back), color = TvColors.Muted)
                }
            }
            ad()
        }
    }
}

@Composable
private fun IntroductionPage() {
    Artwork(Modifier.height(280.dp), TvArt.onboarding)
    Heading(stringResource(Res.string.welcome_title), stringResource(Res.string.welcome_body))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(Feature.REMOTE, Feature.MIRROR, Feature.IPTV).forEach { feature ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconBubble(feature, size = 44.dp)
                Text(
                    stringResource(feature.navLabel()),
                    style = MaterialTheme.typography.labelMedium,
                    color = TvColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun GoalPage(selected: Feature, onGoal: (Feature) -> Unit) {
    Artwork(Modifier.height(170.dp), TvArt.remote)
    Heading(stringResource(Res.string.goal_title), stringResource(Res.string.goal_body))
    listOf(Feature.REMOTE, Feature.MIRROR, Feature.IPTV).forEach { goal ->
        val active = selected == goal
        val shape = RoundedCornerShape(22.dp)
        val fill by animateColorAsState(if (active) TvColors.Raised else TvColors.Surface, label = "goalFill")
        val border by animateColorAsState(if (active) TvColors.Cyan.copy(alpha = 0.6f) else TvColors.Outline, label = "goalBorder")
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(fill)
                .border(1.dp, border, shape)
                .selectable(selected = active, role = Role.RadioButton, onClick = { onGoal(goal) })
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBubble(goal, size = 42.dp)
            Text(
                stringResource(goal.label()),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            RadioButton(selected = active, onClick = null)
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Preview(widthDp = 320, heightDp = 640)
@Composable
private fun OnboardingIntroPreview() {
    TvTheme { OnboardingScaffold(OnboardingUiState(), rememberPagerState { PAGE_COUNT }) }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingBrandPreview() {
    TvTheme { OnboardingScaffold(OnboardingUiState(), rememberPagerState(initialPage = 1) { PAGE_COUNT }) }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingGoalPreview() {
    TvTheme { OnboardingScaffold(OnboardingUiState(), rememberPagerState(initialPage = 2) { PAGE_COUNT }) }
}
