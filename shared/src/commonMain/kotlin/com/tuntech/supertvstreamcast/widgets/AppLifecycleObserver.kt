package com.tuntech.supertvstreamcast.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalInspectionMode
import co.touchlab.kermit.Logger
import com.tuntech.common.extension.FirebaseRemoteConfigUpdateListener
import com.tuntech.common.extension.addUpdateListener
import com.tuntech.common.extension.inject
import com.tuntech.common.util.AppLifecycleListener
import com.tuntech.common.util.LocalAppLifecycleMonitor
import com.tuntech.mmp.MmpManager
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.ad.AdManagerCallback
import com.tuntech.monetization.iap.IapManager
import com.tuntech.supertvstreamcast.constant.AdName
import com.tuntech.supertvstreamcast.ui.AppState
import com.tuntech.supertvstreamcast.ui.Screen
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


/**
 * App-wide lifecycle hooks: the app-open ad when returning from the background, live Remote Config
 * updates, and `screen_view` analytics for the Navigation 3 back stack.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun AppLifecycleObserver(
    appState: AppState,
    startupDelay: Duration = 500.milliseconds,
) {
    if (LocalInspectionMode.current) {
        return
    }

    val lifecycleMonitor = LocalAppLifecycleMonitor.current
    val coroutineScope = rememberCoroutineScope()
    var stopTime = rememberSaveable { Long.MAX_VALUE }

    val lifecycleObserver = object : AppLifecycleListener {
        override fun onStart() {
            val topScreen = appState.currentScreen ?: return
            if (appState.context == null || topScreen.skipAds) {
                return
            }

            val adManager by inject<AdManager>(AdManager::class)
            val backgroundIdleTime =
                (adManager.managers[AdManager.APP_OPEN_AD]?.settings?.backgroundIdleTime
                    ?: 0) * 1000
            val idleTime = Clock.System.now().toEpochMilliseconds() - stopTime
            if (backgroundIdleTime > 0 && idleTime <= backgroundIdleTime) {
                return
            }

            adManager.showAppOpenAd(
                name = AdName.APP_FOREGROUND,
                context = appState.context,
                callback = AdManagerCallback(),
            )
        }

        override fun onStop() {
            stopTime = Clock.System.now().toEpochMilliseconds()
        }
    }

    val remoteConfigUpdateListener = object : FirebaseRemoteConfigUpdateListener {
        override fun onUpdate() {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    Firebase.remoteConfig.activate()

                    val adManager by inject<AdManager>(AdManager::class)
                    val iapManager by inject<IapManager>(IapManager::class)

                    adManager.updateSettings()
                    iapManager.updateSettings()
                } catch (e: Exception) {
                    Logger.e("AppLifecycleObserver", e) { "Failed to activate remote config" }
                }
            }
        }

        override fun onError(exception: Exception) {
            Logger.d("AppLifecycleObserver", exception) { "Failed to update remote config" }
        }
    }

    // Track screen_view whenever the top of the Navigation 3 back stack changes. Only the screen
    // name is sent — never an IP, key or playlist URL.
    LaunchedEffect(appState) {
        val backStack = appState.backStack ?: return@LaunchedEffect
        snapshotFlow { backStack.lastOrNull() as? Screen }
            .distinctUntilChanged()
            .collect { currentScreen ->
                val name = currentScreen?.name ?: return@collect

                MmpManager.shared?.trackEvent(
                    event = "screen_view",
                    parameters = mapOf(
                        "screen_name" to name,
                        "screen_class" to name,
                    )
                )
            }
    }

    DisposableEffect(Unit) {
        // ProcessLifecycleOwner replays onStart to a newly added observer. When the activity is
        // recreated mid-session that replay would show the app-open ad, so stay paused while
        // registering and resume shortly after.
        lifecycleMonitor.pause()
        lifecycleMonitor.start(lifecycleObserver)

        coroutineScope.launch {
            delay(startupDelay)
            lifecycleMonitor.resume()
        }

        val remoteConfigUpdateSub = try {
            Firebase.remoteConfig.addUpdateListener(remoteConfigUpdateListener)
        } catch (e: Exception) {
            Logger.d("AppLifecycleObserver", e) { "Remote config updates unavailable" }
            null
        }

        onDispose {
            lifecycleMonitor.stop(lifecycleObserver)
            remoteConfigUpdateSub?.invoke()
        }
    }
}
