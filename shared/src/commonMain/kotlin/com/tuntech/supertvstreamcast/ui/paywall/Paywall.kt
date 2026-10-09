package com.tuntech.supertvstreamcast.ui.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.common.extension.rememberRemoteConfigString
import com.tuntech.common.util.onDoubleClickProtection
import com.tuntech.monetization.iap.IapProduct
import com.tuntech.monetization.iap.IapProductType
import com.tuntech.monetization.iap.IapSubscriptionPeriod
import com.tuntech.monetization.iap.widget.IapProductItemStyle
import com.tuntech.monetization.iap.widget.IapProductTheme
import com.tuntech.monetization.iap.widget.PremiumCloseButton
import com.tuntech.monetization.iap.widget.PremiumContinueButton
import com.tuntech.monetization.iap.widget.PremiumTermsWidget
import com.tuntech.monetization.iap.widget.ProductCancelMessage
import com.tuntech.monetization.iap.widget.ProductEmptyWidget
import com.tuntech.monetization.iap.widget.ProductsWidget
import com.tuntech.monetization.iap.widget.ProductsWidget2
import com.tuntech.supertvstreamcast.constant.RemoteConfigKey
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvDimens
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.paywall.widget.ProductFeatureWidget
import com.tuntech.supertvstreamcast.widgets.AppBackHandler
import monetization.resources.Res as MonetizationRes
import monetization.resources.or_try_limited_version
import monetization.resources.privacy_policy
import monetization.resources.restore_purchase
import monetization.resources.terms_of_service
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import shared.resources.Res
import com.tuntech.supertvstreamcast.ui.TvArt
import shared.resources.premium_body
import shared.resources.premium_title

/**
 * Which of the two paywalls [PaywallScaffold] renders. Both are the same screen; only the plan
 * list differs. The variant is picked from Remote Config (`IAP_SETTINGS.paywallId`) — see
 * `AppState.navigateToPaywall`.
 */
internal enum class PaywallVariant {
    /** Plans priced per their own billing period. */
    One,

    /** Plans normalised to a per-week price so they can be compared at a glance. */
    Two,
}

/** Paywall 1 — the default variant (`IAP_SETTINGS.paywallId` anything but `"2"`). */
@Composable
fun PaywallScreen(
    isInitial: Boolean = true,
    source: String,
    navigateBack: () -> Unit = {},
    navigateToHome: () -> Unit = {},
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedProduct by viewModel.selectedProduct.collectAsStateWithLifecycle()

    val actions = rememberPaywallActions(
        isInitial = isInitial,
        source = source,
        viewModel = viewModel,
        navigateBack = navigateBack,
        navigateToHome = navigateToHome,
    )

    AppBackHandler(onBack = actions.skip)

    PaywallScaffold(
        variant = PaywallVariant.One,
        isLoading = isLoading,
        products = viewModel.products,
        selectedProduct = selectedProduct,
        onSelectProduct = actions.select,
        onSkip = actions.skip,
        onRestore = actions.restore,
        onRetry = viewModel::getProducts,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaywallScaffold(
    variant: PaywallVariant = PaywallVariant.One,
    isLoading: Boolean = false,
    products: List<IapProduct> = emptyList(),
    selectedProduct: IapProduct? = null,
    onSelectProduct: (IapProduct) -> Unit = {},
    onSkip: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    IapProductTheme(style = PaywallProductStyle) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = TvColors.Background,
            topBar = {
                TopAppBar(
                    title = {},
                    colors = TopAppBarDefaults.topAppBarColors().copy(
                        containerColor = Color.Transparent,
                    ),
                    actions = {
                        PremiumCloseButton(tintColor = TvColors.Text, onClick = onSkip)
                    },
                )
            },
        ) { safePadding ->
            // Hero artwork bleeding under the status bar, fading into the page.
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                Image(
                    painter = painterResource(TvArt.premium),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(TvColors.HeroScrim))
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val maxHeight = this.maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = maxHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(safePadding.calculateTopPadding()))
                        Spacer(modifier = Modifier.weight(1f).heightIn(min = 120.dp))
                        PaywallHeading()
                        Spacer(modifier = Modifier.height(16.dp))
                        ProductFeatureWidget(
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        PaywallProductContent(
                            variant = variant,
                            isLoading = isLoading,
                            products = products,
                            selectedProduct = selectedProduct,
                            onSelectProduct = onSelectProduct,
                            onRetry = onRetry,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PaywallFooter(
                            selectedProduct = selectedProduct,
                            onContinue = {
                                selectedProduct?.let(onSelectProduct) ?: onSkip()
                            },
                            onSkip = onSkip,
                            onRestore = onRestore,
                        )
                    }
                    PremiumTermsWidget(
                        modifier = Modifier
                            .sizeIn(maxWidth = 800.dp)
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(safePadding.calculateBottomPadding()))
                }
            }
        }
    }
}

@Composable
private fun PaywallHeading() {
    Column(
        modifier = Modifier.sizeIn(maxWidth = 800.dp).fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.premium_title),
            style = MaterialTheme.typography.headlineLarge,
            color = TvColors.Text,
        )
        Text(
            stringResource(Res.string.premium_body),
            style = MaterialTheme.typography.bodyLarge,
            color = TvColors.Muted,
        )
    }
}

/**
 * Loading / empty / plan list, rendered by the monetization module's widgets so both paywalls stay
 * in step with the other apps' plan cards.
 */
@Composable
private fun PaywallProductContent(
    modifier: Modifier = Modifier,
    variant: PaywallVariant = PaywallVariant.One,
    isLoading: Boolean = false,
    products: List<IapProduct> = emptyList(),
    selectedProduct: IapProduct? = null,
    onSelectProduct: (IapProduct) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .sizeIn(maxWidth = 800.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) box@{
        if (isLoading) {
            CircularProgressIndicator(
                color = TvColors.Cyan,
                strokeWidth = 2.dp,
            )
            return@box
        }

        if (products.isEmpty()) {
            ProductEmptyWidget(
                onClick = onDoubleClickProtection(onRetry),
            )
            return@box
        }

        when (variant) {
            PaywallVariant.One -> ProductsWidget(
                products = products,
                selectedProduct = selectedProduct,
                selectProduct = onSelectProduct,
            )

            PaywallVariant.Two -> ProductsWidget2(
                products = products,
                selectedProduct = selectedProduct,
                selectProduct = onSelectProduct,
            )
        }
    }
}

/** CTA plus the small print under it. Shared by both paywalls. */
@Composable
private fun PaywallFooter(
    selectedProduct: IapProduct? = null,
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val termsUrl = rememberRemoteConfigString(RemoteConfigKey.TERM_OF_SERVICE_URL)
    val privacyUrl = rememberRemoteConfigString(RemoteConfigKey.PRIVACY_POLICY_URL)

    fun open(url: String) {
        if (url.isNotBlank()) runCatching { uriHandler.openUri(url) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProductCancelMessage(
            productType = selectedProduct?.type,
            color = TvColors.Muted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .sizeIn(maxWidth = 800.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            PremiumContinueButton(
                product = selectedProduct,
                containerBrush = TvColors.Gradient,
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = Color.Transparent,
                    contentColor = TvColors.OnAccent,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = TvColors.OnAccent.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(18.dp),
                onClick = onContinue,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            modifier = Modifier
                .clickable(onClick = onDoubleClickProtection(onSkip))
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            text = stringResource(MonetizationRes.string.or_try_limited_version),
            style = MaterialTheme.typography.labelLarge,
            color = TvColors.Muted,
        )
        Row(
            modifier = Modifier
                .sizeIn(maxWidth = 800.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterLink(
                text = stringResource(MonetizationRes.string.terms_of_service),
                modifier = Modifier.weight(1f),
                onClick = onDoubleClickProtection { open(termsUrl) },
            )
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(0.5f),
                color = TvColors.Outline,
            )
            FooterLink(
                text = stringResource(MonetizationRes.string.restore_purchase),
                modifier = Modifier.weight(1f),
                onClick = onDoubleClickProtection(onRestore),
            )
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(0.5f),
                color = TvColors.Outline,
            )
            FooterLink(
                text = stringResource(MonetizationRes.string.privacy_policy),
                modifier = Modifier.weight(1f),
                onClick = onDoubleClickProtection { open(privacyUrl) },
            )
        }
    }
}

@Composable
private fun FooterLink(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    TextButton(
        modifier = modifier.heightIn(min = 48.dp),
        onClick = onClick,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TvColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Plan-card look; the rendering itself is the monetization module's (see [IapProductTheme]). */
private val PaywallProductStyle = IapProductItemStyle(
    selectedContainerColor = TvColors.Raised,
    unselectedContainerColor = Color.Transparent,
    shape = RoundedCornerShape(18.dp),
    selectedBorder = BorderStroke(2.dp, TvColors.Gradient),
    unselectedBorder = BorderStroke(1.dp, TvColors.Outline),
    contentPadding = PaddingValues(horizontal = TvDimens.Space, vertical = 12.dp),
    titleColor = TvColors.Text,
    priceColor = TvColors.Text,
    secondaryColor = TvColors.Muted,
)

internal val PreviewProducts = listOf(
    IapProduct.Test(
        qonversionId = "weekly",
        storeId = "",
        subscriptionPeriod = IapSubscriptionPeriod(
            unitCount = 1,
            unit = IapSubscriptionPeriod.Unit.Week,
        ),
        trialPeriod = null,
        type = IapProductType.Subscription,
        prettyPrice = "$4.99",
        priceAmount = 4.99,
        currencySymbol = "$",
    ),
    IapProduct.Test(
        qonversionId = "monthly",
        storeId = "",
        subscriptionPeriod = IapSubscriptionPeriod(
            unitCount = 1,
            unit = IapSubscriptionPeriod.Unit.Month,
        ),
        trialPeriod = IapSubscriptionPeriod(
            unitCount = 3,
            unit = IapSubscriptionPeriod.Unit.Day,
        ),
        type = IapProductType.Subscription,
        prettyPrice = "$12.99",
        priceAmount = 12.99,
        currencySymbol = "$",
    ),
    IapProduct.Test(
        qonversionId = "yearly",
        storeId = "",
        subscriptionPeriod = IapSubscriptionPeriod(
            unitCount = 1,
            unit = IapSubscriptionPeriod.Unit.Year,
        ),
        trialPeriod = null,
        type = IapProductType.Subscription,
        prettyPrice = "$59.99",
        priceAmount = 59.99,
        currencySymbol = "$",
    ),
)

@Preview(widthDp = 360, heightDp = 780)
@Preview(widthDp = 320, heightDp = 640)
@Preview(widthDp = 840, heightDp = 700)
@Composable
private fun PreviewPaywallScaffold() {
    TvTheme {
        PaywallScaffold(
            products = PreviewProducts,
            selectedProduct = PreviewProducts[1],
        )
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun PreviewPaywallScaffoldEmpty() {
    TvTheme {
        PaywallScaffold()
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun PreviewPaywallScaffoldLoading() {
    TvTheme {
        PaywallScaffold(isLoading = true)
    }
}
