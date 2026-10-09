package com.tuntech.supertvstreamcast.widgets

import androidx.compose.runtime.Composable
import com.tuntech.common.util.LocalPlatformContext
import com.tuntech.common.widget.dialog.LocalModalContext
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.ad.AdManagerCallback
import com.tuntech.monetization.ad.rememberAdManager
import com.tuntech.supertvstreamcast.constant.AdName
import com.tuntech.supertvstreamcast.ui.app_loading.showAdLoading


enum class BackHandlerType {
    NONE,
    BACK,
    EXIT
}

@Composable
expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
fun AppBackHandler(
    type: BackHandlerType = BackHandlerType.BACK,
    enabled: Boolean = true,
    adManager: AdManager = rememberAdManager(),
    onBack: () -> Unit = {},
) {
    val context = LocalPlatformContext.current
    val modalContext = LocalModalContext.current

    fun goBack() {
        onBack()
        adManager.clearHistory()
    }

    BackHandler(
        enabled = enabled,
    ) {
        if (type == BackHandlerType.EXIT) {
            adManager.forceShowInterstitialAd(
                name = AdName.APP_EXIT,
                context = context,
                callback = AdManagerCallback(
                    onAdLoading = modalContext::showAdLoading,
                    onAdClosed = {
                        modalContext.dismissLoading()
                        goBack()
                    },
                )
            )
            return@BackHandler
        }

        goBack()
    }
}
