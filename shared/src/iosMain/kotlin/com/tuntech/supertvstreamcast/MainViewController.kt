package com.tuntech.supertvstreamcast

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import com.tuntech.supertvstreamcast.domain.TvBrand
import com.tuntech.supertvstreamcast.theme.TvThemeMode
import com.tuntech.supertvstreamcast.ui.storeshot.StoreShot
import com.tuntech.supertvstreamcast.ui.storeshot.StoreShotApp
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

fun MainViewController(): UIViewController {
    val storeShot = storeShotScene()
    val controller = ComposeUIViewController(
        configure = {
            onFocusBehavior = OnFocusBehavior.DoNothing
        }
    ) {
        if (storeShot != null) StoreShotApp(storeShot.shot, storeShot.assets, storeShot.theme, storeShot.brand)
        else MainApp()
    }

    return controller
}

/**
 * `-StoreShot <scene> -StoreShotAssets <url> [-StoreShotTheme light] [-StoreShotBrand LG]` launch
 * arguments (read through the arguments domain of [NSUserDefaults]) open a screenshot scene. Ignored
 * in release binaries.
 */
private class StoreShotLaunch(val shot: StoreShot, val assets: String, val theme: TvThemeMode, val brand: TvBrand)

@OptIn(ExperimentalNativeApi::class)
private fun storeShotScene(): StoreShotLaunch? {
    if (!kotlin.native.Platform.isDebugBinary) return null
    val defaults = NSUserDefaults.standardUserDefaults
    val shot = StoreShot.parse(defaults.stringForKey("StoreShot")) ?: return null
    val theme = if (defaults.stringForKey("StoreShotTheme") == "light") TvThemeMode.LIGHT else TvThemeMode.DARK
    val brand = TvBrand.entries.firstOrNull { it.name.equals(defaults.stringForKey("StoreShotBrand"), ignoreCase = true) } ?: TvBrand.SAMSUNG
    return StoreShotLaunch(shot, defaults.stringForKey("StoreShotAssets") ?: "http://127.0.0.1:8765", theme, brand)
}
