package com.tuntech.supertvstreamcast.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tuntech.supertvstreamcast.ui.tvBackdrop

/** The page wash behind every screen. */
@Composable
fun CommonAppBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().tvBackdrop())
}
