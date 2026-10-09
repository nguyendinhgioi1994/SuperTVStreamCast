package com.tuntech.supertvstreamcast

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val controller = ComposeUIViewController(
        configure = {
            onFocusBehavior = OnFocusBehavior.DoNothing
        }
    ) {
        MainApp()
    }

    return controller
}
