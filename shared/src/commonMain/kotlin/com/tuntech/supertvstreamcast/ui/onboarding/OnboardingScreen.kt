package com.tuntech.supertvstreamcast.ui.onboarding

import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
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
import com.tuntech.supertvstreamcast.analytics.ClickEvent
import com.tuntech.supertvstreamcast.analytics.tracked
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
import shared.resources.promo_noads_body
import shared.resources.promo_noads_title
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
    val isAdReady by adManager.isReady.collectAsStateWithLifecycle()
    var pages by remember { mutableStateOf(onboardingPages(PAGE_COUNT, emptyList())) }
    val pagerState = rememberPagerState(
        pageCount = { pages.size },
    )
    // Full-page natives join the pager once ads are ready, while the user is still on the first
    // page; every ad sits after at least one content page, so the page in view never moves.
    LaunchedEffect(isAdReady) {
        if (!isAdReady || pagerState.currentPage != 0) return@LaunchedEffect
        val ads = listOf(AdName.ONBOARDING_FULL_1, AdName.ONBOARDING_FULL_2)
            .filter { adManager.checkEnabled(AdManager.NATIVE_AD, it) }
            .map { it to (adManager.managers[AdManager.NATIVE_AD]?.getAdSetting(it)?.position ?: PAGE_COUNT) }
        pages = onboardingPages(PAGE_COUNT, ads)
    }

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
        if (pages.getOrNull(pagerState.currentPage) is OnboardingPage.Content) adState.reload()
    }

    OnboardingScaffold(
        state = state,
        pages = pages,
        pagerState = pagerState,
        onNext = {
            val last = pagerState.currentPage == pagerState.pageCount - 1
            ClickEvent.log(if (last) "onboarding_finish" else "onboarding_next", "page" to pagerState.currentPage + 1)
            handleContinue()
        },
        onBack = tracked("onboarding_back", "page" to pagerState.currentPage + 1, action = ::handleBack),
        onBrand = { brand -> ClickEvent.log("onboarding_brand", "selected" to brand); viewModel.brand(brand) },
        onGoal = { goal -> ClickEvent.log("onboarding_goal", "selected" to goal); viewModel.goal(goal) },
        ad = {
            NativeAd(
                name = AdName.INTRODUCTION,
                state = adState,
            )
        },
        fullAd = { name ->
            NativeAd(
                name = name,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
internal fun OnboardingScaffold(
    state: OnboardingUiState,
    pages: List<OnboardingPage>,
    pagerState: PagerState,
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    onBrand: (TvBrand) -> Unit = {},
    onGoal: (Feature) -> Unit = {},
    ad: @Composable () -> Unit = {},
    fullAd: @Composable (String) -> Unit = {},
) {
    val page = pagerState.currentPage
    val step = pages.stepAt(page)
    val onAdPage = pages.getOrNull(page) is OnboardingPage.Ad
    Box(Modifier.fillMaxSize()) {
        CommonAppBackground()
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = TvDimens.ContentMaxWidth).fillMaxSize().safeDrawingPadding().padding(TvDimens.Space),
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
                    stringResource(Res.string.onboarding_step, step + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = TvColors.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PAGE_COUNT) { index ->
                    val fill by animateFloatAsState(if (index <= step) 1f else 0f, tween(TvMotion.Card, easing = TvMotion.Standard), label = "onboardingStep")
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
                    when (val content = pages[index]) {
                        is OnboardingPage.Ad -> AdPage { fullAd(content.name) }
                        is OnboardingPage.Content -> when (content.step) {
                            0 -> IntroductionPage()
                            1 -> {
                                Heading(stringResource(Res.string.brand_title), stringResource(Res.string.brand_body))
                                BrandGrid(state.brand, onBrand)
                            }

                            else -> GoalPage(state.goal, onGoal)
                        }
                    }
                }
            }
            PrimaryCta(
                stringResource(if (page == pages.lastIndex) Res.string.get_started else Res.string.continue_label),
                onClick = onNext,
            )
            if (page > 0) {
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(Res.string.back), color = TvColors.Muted)
                }
            }
            // The small native stays off the full-page ad pages.
            if (!onAdPage) ad()
        }
    }
}

/** A full-page native; while no ad fills it (none loaded, or ads off) the premium pitch shows instead. */
@Composable
private fun AdPage(ad: @Composable () -> Unit) {
    var adHeight by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxWidth().onSizeChanged { adHeight = it.height }) { ad() }
    if (adHeight == 0) {
        Artwork(Modifier.height(240.dp), TvArt.premium)
        Heading(stringResource(Res.string.promo_noads_title), stringResource(Res.string.promo_noads_body))
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

private val PreviewPages = onboardingPages(PAGE_COUNT, emptyList())

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingAdFallbackPreview() {
    val pages = onboardingPages(PAGE_COUNT, listOf("preview" to 2))
    TvTheme { OnboardingScaffold(OnboardingUiState(), pages, rememberPagerState(initialPage = 2) { pages.size }) }
}

@Preview(widthDp = 360, heightDp = 780)
@Preview(widthDp = 320, heightDp = 640)
@Composable
private fun OnboardingIntroPreview() {
    TvTheme { OnboardingScaffold(OnboardingUiState(), PreviewPages, rememberPagerState { PAGE_COUNT }) }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingBrandPreview() {
    TvTheme { OnboardingScaffold(OnboardingUiState(), PreviewPages, rememberPagerState(initialPage = 1) { PAGE_COUNT }) }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingGoalPreview() {
    TvTheme { OnboardingScaffold(OnboardingUiState(), PreviewPages, rememberPagerState(initialPage = 2) { PAGE_COUNT }) }
}
