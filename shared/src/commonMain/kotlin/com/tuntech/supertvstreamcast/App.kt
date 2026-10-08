package com.tuntech.supertvstreamcast

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuntech.supertvstreamcast.platform.*
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.isLocalIpv4
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.*

@Composable fun App() {
    val preferences = rememberAppPreferences()
    val model = viewModel { TvViewModel(preferences) }
    val state by model.state.collectAsStateWithLifecycle()
    val share = rememberScreenSharingAction()
    val requestLan=rememberLanAccessRequest()
    AppBackHandler(state.step in 1..2 || state.player!=null || state.tab!=Feature.HOME || state.connectionOpen,model::back)
    TvTheme {
        TvScaffold(state, model::next, model::back, model::brand, model::goal, model::tab,
            model::connection, {ip,psk -> requestLan {granted -> if(granted) model.connect(ip,psk) else model.report(UiError.PERMISSION)}}, model::disconnect, {key -> requestLan {granted -> if(granted) model.send(key) else model.report(UiError.PERMISSION)}},
            {input ->
                val host=input.trim().substringAfter("://").substringBefore('/').substringBefore(':')
                if(input.trim().startsWith("https://") && isLocalIpv4(host)) requestLan {granted -> if(granted) model.importPlaylist(input) else model.report(UiError.PERMISSION)}
                else model.importPlaylist(input)
            }, model::favorite, {channel ->
                val host=channel.url.substringAfter("://").substringBefore('/').substringBefore(':')
                if(isLocalIpv4(host)) requestLan {granted -> if(granted) model.play(channel) else model.report(UiError.PERMISSION)}
                else model.play(channel)
            },
            onPlayerError = { model.report(UiError.PLAYER) },
            onShare = share?.let { action -> { if (!action()) model.report(UiError.MIRROR) } },
        )
    }
}
