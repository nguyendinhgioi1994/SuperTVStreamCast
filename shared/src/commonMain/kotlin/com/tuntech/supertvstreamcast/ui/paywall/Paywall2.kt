package com.tuntech.supertvstreamcast.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.widgets.AppBackHandler
import org.koin.compose.viewmodel.koinViewModel

/**
 * Paywall 2 (`IAP_SETTINGS.paywallId == "2"`): the same screen as [PaywallScreen] with plans
 * normalised to a per-week price. Flow and chrome are shared through [rememberPaywallActions] and
 * [PaywallScaffold].
 */
@Composable
fun Paywall2Screen(
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
        variant = PaywallVariant.Two,
        isLoading = isLoading,
        products = viewModel.products,
        selectedProduct = selectedProduct,
        onSelectProduct = actions.select,
        onSkip = actions.skip,
        onRestore = actions.restore,
        onRetry = viewModel::getProducts,
    )
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun PreviewPaywall2Scaffold() {
    TvTheme {
        PaywallScaffold(
            variant = PaywallVariant.Two,
            products = PreviewProducts,
            selectedProduct = PreviewProducts[1],
        )
    }
}
