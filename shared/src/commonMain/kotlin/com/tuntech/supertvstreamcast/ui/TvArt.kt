package com.tuntech.supertvstreamcast.ui

import com.tuntech.supertvstreamcast.theme.TvColors
import org.jetbrains.compose.resources.DrawableResource
import shared.resources.*

/** Text-free illustrations; each has a night and a daylight rendering that follows the theme. */
internal object TvArt {
    val home: DrawableResource get() = if (TvColors.isDark) Res.drawable.art_home_dark else Res.drawable.art_home_light
    val mirror: DrawableResource get() = if (TvColors.isDark) Res.drawable.art_mirror_dark else Res.drawable.art_mirror_light
    val iptv: DrawableResource get() = if (TvColors.isDark) Res.drawable.art_iptv_dark else Res.drawable.art_iptv_light
    val remote: DrawableResource get() = if (TvColors.isDark) Res.drawable.art_remote_dark else Res.drawable.art_remote_light
    val onboarding: DrawableResource get() = if (TvColors.isDark) Res.drawable.art_onboarding_dark else Res.drawable.art_onboarding_light
    val premium: DrawableResource get() = if (TvColors.isDark) Res.drawable.art_premium_dark else Res.drawable.art_premium_light
}
