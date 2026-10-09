package com.tuntech.supertvstreamcast.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import com.tuntech.common.util.LocalAppLocale
import com.tuntech.common.util.LocalPlatformContext
import com.tuntech.common.widget.dialog.LocalModalContext
import com.tuntech.common.widget.theme.Theme
import com.tuntech.mmp.MmpManager
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.ad.AdManagerCallback
import com.tuntech.monetization.iap.IapManager
import com.tuntech.monetization.iap.IapProduct
import com.tuntech.monetization.iap.IapPurchaseResult
import com.tuntech.monetization.iap.discount.showIapDiscountDialog
import com.tuntech.supertvstreamcast.constant.AdName
import com.tuntech.supertvstreamcast.ui.app_loading.showAdLoading
import com.tuntech.supertvstreamcast.ui.app_loading.showAppLoading
import com.tuntech.supertvstreamcast.widgets.dialog.showAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import monetization.resources.Res as MonetizationRes
import monetization.resources.premium_purchase_fail_description
import monetization.resources.premium_purchase_fail_title
import monetization.resources.premium_purchase_success_description
import monetization.resources.premium_purchase_success_title
import monetization.resources.premium_purchasing_title
import monetization.resources.premium_restoring_title
import org.koin.compose.koinInject
import shared.resources.Res
import shared.resources.ok
import shared.resources.please_wait_a_moment

/**
 * Skip / purchase / restore behaviour shared by [PaywallScreen] (paywall 1) and [Paywall2Screen]
 * (paywall 2). The variants are only allowed to differ in how the plans are laid out.
 */
@Stable
internal class PaywallActions(
    /** Dismiss the paywall: interstitial → navigate away → win-back discount offer. */
    val skip: () -> Unit,
    /** Select a plan and immediately start its purchase. */
    val select: (IapProduct) -> Unit,
    /** Restore a previous purchase. */
    val restore: () -> Unit,
)

@Composable
internal fun rememberPaywallActions(
    isInitial: Boolean,
    source: String,
    viewModel: PaywallViewModel,
    navigateBack: () -> Unit,
    navigateToHome: () -> Unit,
    adManager: AdManager = koinInject(),
    iapManager: IapManager = koinInject(),
): PaywallActions {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val modalContext = LocalModalContext.current

    fun skipPremium() {
        adManager.showInterstitialAd(
            name = AdName.PREMIUM,
            context = context,
            callback = AdManagerCallback(
                onAdLoading = modalContext::showAdLoading,
                onAdShowedFullScreenContent = modalContext::dismissLoading,
                onAdClosed = {
                    modalContext.dismissLoading()
                    LocalAppLocale.restoreLocale(context)
                    if (isInitial) {
                        navigateToHome()
                    } else {
                        navigateBack()
                    }

                    MmpManager.shared?.trackEvent(
                        "iap_close_${source}",
                    )
                    modalContext.modalScope.launch(Dispatchers.Default) {
                        iapManager.increaseCancelCount()
                        modalContext.showIapDiscountDialog(
                            source = source,
                            statusBarTheme = Theme.LIGHT,
                            navBarTheme = Theme.LIGHT,
                        )
                    }
                },
            )
        )
    }

    fun showPurchaseSuccessDialog() {
        adManager.deactivate()
        adManager.clean()

        modalContext.showAlertDialog(
            title = MonetizationRes.string.premium_purchase_success_title,
            description = MonetizationRes.string.premium_purchase_success_description,
            positiveAction = Res.string.ok,
            onPositiveAction = ::skipPremium,
        )
    }

    fun showPurchaseFailDialog() {
        modalContext.showAlertDialog(
            title = MonetizationRes.string.premium_purchase_fail_title,
            description = MonetizationRes.string.premium_purchase_fail_description,
            positiveAction = Res.string.ok,
        )
    }

    fun selectProduct(product: IapProduct) {
        viewModel.selectProduct(product)

        modalContext.showAppLoading(
            title = MonetizationRes.string.premium_purchasing_title,
            description = Res.string.please_wait_a_moment,
        )

        coroutineScope.launch(Dispatchers.Main) {
            try {
                val result = iapManager.purchaseProduct(
                    context = context,
                    product = product,
                )

                modalContext.dismissLoading()

                when (result) {
                    is IapPurchaseResult.Success -> {
                        showPurchaseSuccessDialog()
                        MmpManager.shared?.trackEvent(
                            "iap_${product.qonversionId}_${source}",
                        )
                    }

                    IapPurchaseResult.Cancelled -> {
                        MmpManager.shared?.trackEvent(
                            "iap_source_${source}_cancel", mapOf(
                                "productId" to product.qonversionId,
                            )
                        )
                    }

                    is IapPurchaseResult.Failure -> {
                        MmpManager.shared?.trackEvent(
                            "iap_source_${source}_fail", mapOf(
                                "productId" to product.qonversionId,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                modalContext.dismissLoading()
                showPurchaseFailDialog()

                Logger.e(
                    throwable = e,
                    tag = "Paywall",
                ) {
                    "Purchase failed for product ${product.qonversionId}"
                }
            }
        }
    }

    fun restorePurchase() {
        modalContext.showAppLoading(
            title = MonetizationRes.string.premium_restoring_title,
            description = Res.string.please_wait_a_moment,
        )

        coroutineScope.launch(Dispatchers.Main) {
            iapManager.restorePurchase()

            modalContext.dismissLoading()

            if (iapManager.isActive.value) {
                showPurchaseSuccessDialog()
                return@launch
            }

            showPurchaseFailDialog()
        }
    }

    return PaywallActions(
        skip = ::skipPremium,
        select = ::selectProduct,
        restore = ::restorePurchase,
    )
}
