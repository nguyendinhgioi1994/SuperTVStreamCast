package com.tuntech.supertvstreamcast.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.tuntech.common.util.LocalPlatformContext
import com.tuntech.common.util.PlatformContext
import com.tuntech.common.util.PlatformUtil
import com.tuntech.common.widget.dialog.ModalStack
import com.tuntech.monetization.iap.IapManager

val LocalAppState = staticCompositionLocalOf {
    AppState(
        backStack = null,
        lifecycleOwner = null,
        modalContext = null,
        context = null,
    )
}

@Composable
fun AppStateProvider(
    appState: AppState = rememberAppState(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppState provides appState,
    ) {
        content()
    }
}

@Composable
fun rememberAppState(
    rootBackStack: NavBackStack<NavKey> = rememberScreenBackStack(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    modalContext: ModalStack? = null,
    context: PlatformContext = LocalPlatformContext.current,
) = remember(rootBackStack) {
    AppState(rootBackStack, lifecycleOwner, modalContext, context)
}


class AppState(
    val backStack: NavBackStack<NavKey>?,
    val lifecycleOwner: LifecycleOwner?,
    var modalContext: ModalStack?,
    val context: PlatformContext?,
) {
    val isReady: Boolean
        get() = lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.CREATED) == true

    /** Screen currently on top of the back stack, or null before the first entry is pushed. */
    val currentScreen: Screen?
        get() = backStack?.lastOrNull() as? Screen

    fun navigate(screen: Screen) {
        if (!isReady) {
            return
        }
        backStack?.add(screen)
    }

    /**
     * Replaces the current back stack. When [popUpToInclusive] is null the whole stack is cleared;
     * otherwise the last entry matching the predicate and everything above it is removed before
     * [screen] is pushed.
     */
    fun replace(screen: Screen, popUpToInclusive: ((NavKey) -> Boolean)? = null) {
        if (!isReady) {
            return
        }
        val backStack = backStack ?: return
        if (popUpToInclusive == null) {
            backStack.clear()
        } else {
            val index = backStack.indexOfLast(popUpToInclusive)
            if (index >= 0) {
                while (backStack.size > index) {
                    backStack.removeAt(backStack.size - 1)
                }
            }
        }
        backStack.add(screen)
    }

    fun navigateBack() {
        val context = context ?: return
        val backStack = backStack ?: return
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            return
        }
        PlatformUtil.exitApp(context)
    }

    /**
     * Rewrites the entry below the top of the back stack — how a screen hands a result back to its
     * caller in Navigation 3. Returning null from [transform] leaves the entry untouched.
     */
    fun setPreviousResult(transform: (NavKey) -> NavKey?) {
        val backStack = backStack ?: return
        val prevIndex = backStack.lastIndex - 1
        if (prevIndex < 0) {
            return
        }
        transform(backStack[prevIndex])?.let { backStack[prevIndex] = it }
    }

    fun replaceToDashboard(isPaywallClosed: Boolean = false) {
        replace(Screen.Dashboard(isPaywallClosed))
    }

    fun replaceToOnboarding() {
        replace(Screen.Onboarding)
    }

    fun navigateToPaywall(isInitial: Boolean = true, source: String) {
        // Keep at most one paywall on the stack — re-opening it from a deeper screen
        // otherwise stacks a second copy that the user has to dismiss twice.
        backStack?.removeAll { it is Screen.Paywall || it is Screen.Paywall2 }
        navigate(paywallScreen(isInitial, source))
    }

    /**
     * Pops the paywall and reports back to the Dashboard below it whether the paywall was
     * dismissed.
     */
    fun closePaywallWithResult(isPaywallClosed: Boolean) {
        setPreviousResult { prev ->
            (prev as? Screen.Dashboard)?.copy(isPaywallClosed = isPaywallClosed)
        }
        navigateBack()
    }

    /** Which of the two paywalls to open, per Remote Config (`IAP_SETTINGS.paywallId`). */
    private fun paywallScreen(isInitial: Boolean, source: String): Screen =
        when (IapManager.shared.paywallId) {
            "2" -> Screen.Paywall2(isInitial, source)
            else -> Screen.Paywall(isInitial, source)
        }

    fun replaceToPaywall(isInitial: Boolean = true, source: String) {
        navigate(paywallScreen(isInitial, source))
    }
}
