package com.tuntech.supertvstreamcast

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuntech.supertvstreamcast.platform.*
import com.tuntech.supertvstreamcast.data.isRemoteSource
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.*

@Composable fun App() {
    val preferences = rememberAppPreferences()
    val model = viewModel { TvViewModel(preferences) }
    val state by model.state.collectAsStateWithLifecycle()
    val share = rememberScreenSharingAction()
    val requestLan = rememberLanAccessRequest()

    /** Every LAN action asks for local-network access at the point of use. */
    val lan: (() -> Unit) -> Unit = { action -> requestLan { granted -> if (granted) action() else model.report(UiError.PERMISSION) } }
    fun isLanUrl(url: String) = isLocalIpv4(url.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':'))
    /** LAN hosts (playlist servers, Xtream panels, streams) need local-network access first. */
    val maybeLan: (String, () -> Unit) -> Unit = { url, action -> if (isLanUrl(url)) lan(action) else action() }
    val pickPlaylist = rememberFilePicker(PLAYLIST_MAX_BYTES, model::importPlaylistFile)
    val pickGuide = rememberFilePicker(GUIDE_MAX_BYTES, model::importGuideFile)
    AppBackHandler(state.step in 1..2 || state.player != null || state.tab != Feature.HOME || state.connectionOpen, model::back)
    val actions = TvActions(
        next = model::next, back = model::back, brand = model::brand, goal = model::goal, tab = model::tab,
        connection = model::connection, connect = { ip, psk -> lan { model.connect(ip, psk) } }, disconnect = model::disconnect,
        scan = { lan(model::scan) }, pick = { device -> lan { model.pick(device) } },
        key = model::send, text = model::sendText, move = model::move, click = model::click,
        loadApps = model::loadApps, launch = model::launch,
        importPlaylist = { input -> if (isRemoteSource(input)) maybeLan(input) { model.importPlaylist(input) } else model.importPlaylist(input) },
        favorite = model::favorite,
        play = { channel -> maybeLan(channel.url) { model.play(channel) } },
        zap = model::zap, playerError = { model.report(UiError.PLAYER) },
        share = share?.let { action -> { if (!action()) model.report(UiError.MIRROR) } },
        pickPlaylistFile = pickPlaylist, pickGuideFile = pickGuide, clearError = model::clearError,
        importXtream = { server, user, pass -> maybeLan(XtreamApi.normalizeServer(server).orEmpty()) { model.importXtream(server, user, pass) } },
        playStream = { url, title -> maybeLan(url) { model.playStream(url, title) } },
        importGuide = { url -> maybeLan(url) { model.importGuide(url) } },
        providerGuide = { maybeLan(state.guideUrl, model::useProviderGuide) },
    )
    TvTheme { TvScaffold(state, actions) }
}
