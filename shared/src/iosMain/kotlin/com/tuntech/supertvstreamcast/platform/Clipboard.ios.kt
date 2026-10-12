package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable actual fun rememberCopyText(): (String) -> Unit = remember { { text -> UIPasteboard.generalPasteboard.string = text } }
