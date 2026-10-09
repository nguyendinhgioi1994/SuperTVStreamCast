package com.tuntech.supertvstreamcast.ui.common.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import com.tuntech.common.widget.dialog.LocalModalContext
import com.tuntech.common.widget.effect.AppUpdateEffect
import com.tuntech.common.widget.effect.InAppUpdateSettings
import com.tuntech.common.widget.effect.OnAppUpdateInstallCallback
import com.tuntech.monetization.iap.IapManager
import com.tuntech.supertvstreamcast.constant.IapConstant
import com.tuntech.supertvstreamcast.ui.LocalAppState
import com.tuntech.supertvstreamcast.widgets.dialog.showAlertDialog
import shared.resources.Res
import shared.resources.cancel
import shared.resources.get_update
import shared.resources.new_update_description
import shared.resources.new_update_title
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Dashboard start-up flow:
 * 1. wait for the entitlement check and show the startup paywall when enabled;
 * 2. once the user is premium or has closed that paywall, run the in-app update check
 *    (`AppUpdateEffect` also triggers the in-app review on its own).
 *
 * Ads are initialised by `SplashViewModel`, not here.
 */
@OptIn(ExperimentalAtomicApi::class)
@Composable
fun AppFlowEffect(
    isPaywallClosed: Boolean = false,
) {
    val appState = LocalAppState.current

    var isIapChecked by rememberSaveable { mutableStateOf(false) }

    // Step 1: Show Paywall
    if (!isIapChecked) {
        LaunchedEffect(Unit) {
            IapManager.shared.isActiveShared.collect {
                if (it) {
                    isIapChecked = true
                    return@collect
                }

                if (!IapManager.shared.isEntitlementChecked.load()) {
                    return@collect
                }

                isIapChecked = true

                if (!IapManager.shared.isStartupEnabled || IapManager.shared.isFirstStartupStarted) {
                    return@collect
                }

                appState.navigateToPaywall(
                    isInitial = false,
                    source = IapConstant.IAP_SOURCE_HOME,
                )
            }
        }
        return
    }

    // Skip everything until the paywall is closed or the user is premium
    if (!IapManager.shared.isActive.value && !isPaywallClosed) {
        return
    }

    // Step 2: App Update + App Review
    val modalContext = LocalModalContext.current

    fun showAppUpdateDialog(
        settings: InAppUpdateSettings,
        callback: OnAppUpdateInstallCallback
    ) {
        if (settings.type == InAppUpdateSettings.Type.IMMEDIATE) {
            modalContext.showAlertDialog(
                title = Res.string.new_update_title,
                description = Res.string.new_update_description,
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                positiveAction = Res.string.get_update,
                onPositiveAction = callback::invoke,
            )
            return
        }
        modalContext.showAlertDialog(
            title = Res.string.new_update_title,
            description = Res.string.new_update_description,
            negativeAction = Res.string.cancel,
            positiveAction = Res.string.get_update,
            onPositiveAction = callback::invoke,
        )
    }

    AppUpdateEffect(
        onUpdateInstalled = ::showAppUpdateDialog,
    )
}
