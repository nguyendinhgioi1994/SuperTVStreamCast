package com.tuntech.supertvstreamcast.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.common.util.Platform
import com.tuntech.common.util.PlatformOS
import com.tuntech.monetization.iap.rememberIapActive
import com.tuntech.supertvstreamcast.constant.IapConstant
import com.tuntech.supertvstreamcast.data.isRemoteSource
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.GUIDE_FILE_MAX_BYTES
import com.tuntech.supertvstreamcast.domain.PLAYLIST_MAX_BYTES
import com.tuntech.supertvstreamcast.domain.XtreamApi
import com.tuntech.supertvstreamcast.domain.isLocalIpv4
import com.tuntech.supertvstreamcast.platform.rememberFilePicker
import com.tuntech.supertvstreamcast.platform.rememberLanAccessRequest
import com.tuntech.supertvstreamcast.platform.rememberScreenSharingAction
import com.tuntech.supertvstreamcast.ui.TvActions
import com.tuntech.supertvstreamcast.ui.TvScaffold
import com.tuntech.supertvstreamcast.ui.TvViewModel
import com.tuntech.supertvstreamcast.ui.iptv.ImportLinks
import com.tuntech.supertvstreamcast.ui.iptv.formatClock
import com.tuntech.supertvstreamcast.platform.rememberReminderScheduler
import com.tuntech.supertvstreamcast.constant.RemoteConfigKey
import com.tuntech.supertvstreamcast.domain.Reminder
import com.tuntech.supertvstreamcast.domain.parseIptvLimits
import com.tuntech.supertvstreamcast.domain.reminderId
import com.tuntech.common.extension.getString
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.remoteConfig
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import shared.resources.iptv_reminder_body
import com.tuntech.supertvstreamcast.ui.iptv.IptvActions
import androidx.compose.runtime.LaunchedEffect
import com.tuntech.supertvstreamcast.ui.iptv.IptvViewModel
import com.tuntech.supertvstreamcast.domain.buildImportLink
import com.tuntech.common.util.shareText
import org.jetbrains.compose.resources.stringResource
import shared.resources.Res
import shared.resources.iptv_share_message
import com.tuntech.supertvstreamcast.ui.UiError
import com.tuntech.supertvstreamcast.ui.common.base.AppFlowEffect
import com.tuntech.supertvstreamcast.widgets.AppBackHandler
import com.tuntech.supertvstreamcast.widgets.BackHandlerType
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root of the app after the entry flow: the Home / Remote / Mirror / IPTV / Settings tabs. Runs
 * [AppFlowEffect] (startup paywall, then the in-app update check).
 */
@Composable
fun DashboardScreen(
    isPaywallClosed: Boolean = false,
    navigateToPaywall: (String) -> Unit,
    navigateBack: () -> Unit,
    model: TvViewModel = koinViewModel(),
    iptvModel: IptvViewModel = koinViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val iptv by iptvModel.state.collectAsStateWithLifecycle()
    val context = coil3.compose.LocalPlatformContext.current
    val isPremium = rememberIapActive()
    val themeRepository = org.koin.compose.koinInject<com.tuntech.supertvstreamcast.data.repository.ThemeRepository>()
    val share = rememberScreenSharingAction()
    val requestLan = rememberLanAccessRequest()

    /** Every LAN action asks for local-network access at the point of use. */
    val lan: (() -> Unit) -> Unit = { action -> requestLan { granted -> if (granted) action() else model.report(UiError.PERMISSION) } }
    fun isLanUrl(url: String) = isLocalIpv4(url.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':'))
    val pickPlaylist = rememberFilePicker(PLAYLIST_MAX_BYTES, iptvModel::addPlaylistFile)
    val pickGuide = rememberFilePicker(GUIDE_FILE_MAX_BYTES, iptvModel::importGuideFile)
    val iptvLan: (String, () -> Unit) -> Unit = { url, action ->
        if (isLanUrl(url)) requestLan { granted -> if (granted) action() else iptvModel.report(UiError.PERMISSION) } else action()
    }
    fun sourceUrl(id: String) = iptv.sources.firstOrNull { it.id == id }?.url.orEmpty()
    val shareMessage = stringResource(Res.string.iptv_share_message)

    // Free-tier limits come from Remote Config; reaching one opens the paywall.
    val limits = remember { parseIptvLimits(runCatching { Firebase.remoteConfig.getString(RemoteConfigKey.IPTV_SETTINGS) }.getOrDefault("")) }
    LaunchedEffect(isPremium, limits) { iptvModel.access(isPremium, limits) }
    LaunchedEffect(iptv.premiumRequired) {
        if (iptv.premiumRequired) { iptvModel.clearPremiumRequired(); navigateToPaywall(IapConstant.IAP_SOURCE_IPTV_LIMIT) }
    }
    val reminders = rememberReminderScheduler()
    val scope = rememberCoroutineScope()

    // A playlist link that opened the app is offered on the IPTV tab once the saved library is loaded.
    val importLink by ImportLinks.pending.collectAsStateWithLifecycle()
    LaunchedEffect(importLink, iptv.loaded) {
        val link = importLink
        if (link != null && iptv.loaded) { ImportLinks.consume(); model.tab(Feature.IPTV); iptvModel.offerImport(link) }
    }

    // Inside IPTV, back first closes the player or returns to the previous IPTV screen.
    val iptvNested = state.tab == Feature.IPTV && iptv.canGoBack && !state.connectionOpen
    val isNested = state.tab != Feature.HOME || state.connectionOpen
    AppBackHandler(
        enabled = isNested,
        type = BackHandlerType.BACK,
        onBack = { if (iptvNested) iptvModel.back() else model.back() },
    )
    AppBackHandler(
        enabled = Platform.os != PlatformOS.IOS && !isNested,
        type = BackHandlerType.EXIT,
        onBack = navigateBack,
    )

    AppFlowEffect(isPaywallClosed = isPaywallClosed)

    val actions = TvActions(
        back = model::back, brand = model::brand, tab = { tab -> iptvModel.closePlayer(); model.tab(tab) },
        connection = model::connection, connect = { ip, psk -> lan { model.connect(ip, psk) } }, disconnect = model::disconnect,
        scan = { lan(model::scan) }, pick = { device -> lan { model.pick(device) } },
        key = model::send, text = model::sendText, move = model::move, click = model::click,
        loadApps = model::loadApps, launch = model::launch,
        share = share?.let { action -> { if (!action()) model.report(UiError.MIRROR) } },
        premium = if (isPremium) null else ({ navigateToPaywall(IapConstant.IAP_SOURCE_SETTING) }),
        wake = { ip -> lan { model.wake(ip) } }, forgetTvs = model::forgetTvs,
        theme = themeRepository::select,
    )
    // LAN-hosted playlists, Xtream panels and streams ask for local-network access first.
    val iptvActions = IptvActions(
        back = iptvModel::back, clearError = iptvModel::clearError, clearNotice = iptvModel::clearNotice, cancelImport = iptvModel::cancelImport,
        open = { id -> iptvLan(sourceUrl(id)) { iptvModel.open(id) } }, refresh = { id -> iptvLan(sourceUrl(id)) { iptvModel.refresh(id) } },
        rename = iptvModel::rename, refreshEvery = iptvModel::refreshEvery, delete = iptvModel::delete,
        share = { source -> source.shareUrl?.let { url -> context.shareText("$shareMessage\n${buildImportLink(source.name, url)}\n$url") } },
        addPlaylist = { input, name -> if (isRemoteSource(input)) iptvLan(input) { iptvModel.addPlaylist(input, name) } else iptvModel.addPlaylist(input, name) },
        pickPlaylistFile = pickPlaylist,
        addXtream = { server, user, pass, name -> iptvLan(XtreamApi.normalizeServer(server).orEmpty()) { iptvModel.addXtream(server, user, pass, name) } },
        addStream = { url, title -> iptvLan(url) { iptvModel.addStream(url, title) } },
        acceptImport = { iptvLan(iptv.pendingImport?.url.orEmpty(), iptvModel::acceptImport) }, dismissImport = iptvModel::dismissImport,
        importGuide = { url -> iptvLan(url) { iptvModel.importGuide(url) } }, pickGuideFile = pickGuide,
        play = { channel, queue -> iptvLan(channel.url) { iptvModel.play(channel, queue) } }, zap = iptvModel::zap,
        favorite = iptvModel::favorite, hide = iptvModel::hide,
        openLibrary = iptvModel::openLibrary, clear = iptvModel::clear, openProgrammes = iptvModel::openProgrammes,
        playEpisode = { episode -> iptvLan(episode.url) { iptvModel.playEpisode(episode) } }, retrySeries = { series -> iptvModel.openSeries(series) },
        submitPasscode = iptvModel::submitPasscode, changePasscode = iptvModel::changePasscode,
        closePlayer = iptvModel::closePlayer, expand = iptvModel::expand,
        openGuide = iptvModel::openGuide, openScanner = iptvModel::openScanner, scanned = iptvModel::scanned,
        remind = { channel, programme, minutes ->
            scope.launch {
                val reminder = Reminder(reminderId(channel.url, programme.start), channel.url, channel.title, programme.start, programme.title, programme.start - minutes * 60L)
                val body = org.jetbrains.compose.resources.getString(Res.string.iptv_reminder_body, channel.title, formatClock(programme.start))
                if (reminders.schedule(reminder, programme.title, body)) iptvModel.addReminder(reminder) else iptvModel.reminderDenied()
            }
        },
        unremind = { id -> reminders.cancel(id); iptvModel.removeReminder(id) },
        progress = iptvModel::progress, ended = iptvModel::ended, playerError = { iptvModel.report(UiError.PLAYER) },
    )
    TvScaffold(state, actions, iptv, iptvActions)
}
