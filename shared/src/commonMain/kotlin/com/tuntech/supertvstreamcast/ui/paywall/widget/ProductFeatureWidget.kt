package com.tuntech.supertvstreamcast.ui.paywall.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.Glyph
import com.tuntech.supertvstreamcast.ui.GlyphIcon
import org.jetbrains.compose.resources.stringResource
import shared.resources.Res
import shared.resources.premium_feature_ads
import shared.resources.premium_feature_iptv
import shared.resources.premium_feature_remote

/** What Premium changes today: the app without ads. Nothing here promises unreleased features. */
@Composable
internal fun ProductFeatureWidget(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.sizeIn(maxWidth = 800.dp).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            Res.string.premium_feature_ads,
            Res.string.premium_feature_remote,
            Res.string.premium_feature_iptv,
        ).forEach { feature ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(24.dp).background(TvColors.Cyan.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.CHECK, Modifier.size(14.dp), TvColors.Cyan)
                }
                Text(
                    stringResource(feature),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvColors.Text,
                )
            }
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun ProductFeaturePreview() {
    TvTheme { ProductFeatureWidget() }
}
