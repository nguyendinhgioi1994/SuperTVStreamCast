package com.tuntech.supertvstreamcast.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.tuntech.monetization.ad.widget.AdActionTheme
import com.tuntech.monetization.ad.widget.AdBadgeTheme
import com.tuntech.monetization.ad.widget.AdNativeTheme
import com.tuntech.monetization.ad.widget.AdRatingTheme
import com.tuntech.monetization.ad.widget.AdSkeletonTheme
import com.tuntech.monetization.ad.widget.AdTheme

@Composable
private fun adActionTheme(): AdActionTheme = AdActionTheme(
    shape = RoundedCornerShape(18.dp),
    brush = TvColors.Gradient,
    textStyle = MaterialTheme.typography.titleMedium,
    textColor = TvColors.OnAccent,
)

/** Native / banner ads on the active TV Space palette. */
@Composable
fun TvAdTheme(
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(1.dp, TvColors.Outline)
    AdTheme(
        bannerSkeletonTheme = AdSkeletonTheme(
            backgroundColor = TvColors.Surface,
            iconColor = TvColors.Raised,
            mediaColor = TvColors.Raised,
            titleColor = TvColors.Raised,
            descriptionColor = TvColors.Raised,
            border = border,
            actionTheme = adActionTheme(),
        ),
        nativeSkeletonTheme = AdSkeletonTheme(
            backgroundColor = TvColors.Surface,
            shape = shape,
            border = border,
            iconColor = TvColors.Raised,
            mediaColor = TvColors.Raised,
            titleColor = TvColors.Raised,
            descriptionColor = TvColors.Raised,
            actionTheme = adActionTheme(),
        ),
        nativeAdTheme = AdNativeTheme(
            backgroundColor = TvColors.Surface,
            shape = shape,
            border = border,
            primaryTextStyle = MaterialTheme.typography.titleMedium.copy(color = TvColors.Text),
            secondaryTextStyle = MaterialTheme.typography.labelSmall.copy(color = TvColors.Muted),
            bodyTextStyle = MaterialTheme.typography.labelMedium.copy(color = TvColors.Muted),
            badgeTheme = AdBadgeTheme(
                backgroundColor = TvColors.Raised,
                border = BorderStroke(1.dp, TvColors.Cyan),
                textColor = TvColors.Cyan,
            ),
            ratingTheme = AdRatingTheme(
                tintEmpty = TvColors.Outline,
                tintFilled = TvColors.Coral,
            ),
            actionTheme = adActionTheme(),
        ),
        content = content,
    )
}
