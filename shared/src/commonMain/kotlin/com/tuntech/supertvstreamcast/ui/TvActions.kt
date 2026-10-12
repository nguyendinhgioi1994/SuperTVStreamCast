package com.tuntech.supertvstreamcast.ui

import com.tuntech.supertvstreamcast.domain.*

/** Callbacks from screens to the ViewModel; screens stay stateless apart from local form/filter state. IPTV has its own [com.tuntech.supertvstreamcast.ui.iptv.IptvActions]. */
class TvActions(
    val back: () -> Unit = {}, val brand: (TvBrand) -> Unit = {},
    val tab: (Feature) -> Unit = {}, val connection: (Boolean) -> Unit = {},
    val connect: (String, String) -> Unit = { _, _ -> }, val disconnect: () -> Unit = {},
    val scan: () -> Unit = {}, val pick: (TvDevice) -> Unit = {},
    val key: (RemoteKey) -> Unit = {}, val text: (String) -> Unit = {},
    val move: (Int, Int) -> Unit = { _, _ -> }, val click: () -> Unit = {},
    val loadApps: () -> Unit = {}, val launch: (TvApp) -> Unit = {},
    val share: (() -> Unit)? = null,
    /** Opens the paywall from the given IAP source; its entry points are hidden by IapActiveBox once the user is premium. */
    val premium: (String) -> Unit = {},
    val wake: (String) -> Unit = {}, val forgetTvs: () -> Unit = {},
    val theme: (com.tuntech.supertvstreamcast.theme.TvThemeMode) -> Unit = {},
    /** A Home quick-access card; the host may show an interstitial before switching tab. Defaults to [tab]. */
    val openFeature: ((Feature) -> Unit)? = null,
    /** A free user tapped a premium-only feature; the host opens the premium gate. */
    val locked: (PremiumFeature) -> Unit = {},
)
