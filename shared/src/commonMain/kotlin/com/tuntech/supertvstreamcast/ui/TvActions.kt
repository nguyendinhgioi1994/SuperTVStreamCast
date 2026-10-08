package com.tuntech.supertvstreamcast.ui

import com.tuntech.supertvstreamcast.domain.*

/** Callbacks from screens to the ViewModel; screens stay stateless apart from local form/filter state. */
class TvActions(
    val next: () -> Unit = {}, val back: () -> Unit = {}, val brand: (TvBrand) -> Unit = {},
    val goal: (Feature) -> Unit = {}, val tab: (Feature) -> Unit = {}, val connection: (Boolean) -> Unit = {},
    val connect: (String, String) -> Unit = { _, _ -> }, val disconnect: () -> Unit = {},
    val scan: () -> Unit = {}, val pick: (TvDevice) -> Unit = {},
    val key: (RemoteKey) -> Unit = {}, val text: (String) -> Unit = {},
    val move: (Int, Int) -> Unit = { _, _ -> }, val click: () -> Unit = {},
    val loadApps: () -> Unit = {}, val launch: (TvApp) -> Unit = {},
    val importPlaylist: (String) -> Unit = {}, val favorite: (String) -> Unit = {}, val play: (Channel) -> Unit = {},
    val zap: (Int) -> Unit = {}, val playerError: () -> Unit = {}, val share: (() -> Unit)? = null,
)
