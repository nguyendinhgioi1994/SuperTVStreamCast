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
import com.tuntech.supertvstreamcast.ui.iptv.IptvScreen
import com.tuntech.supertvstreamcast.analytics.ClickEvent
import com.tuntech.supertvstreamcast.domain.RemoteKey
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
import org.koin.compose.koinInject
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import com.tuntech.common.extension.rememberRemoteConfigString
import com.tuntech.common.util.AppLogger
import com.tuntech.common.util.BuildConfig
import com.tuntech.common.util.getAppName
import com.tuntech.common.util.getStoreUrl
import com.tuntech.common.util.openStoreDetails
import com.tuntech.common.util.openStoreSubscription
import com.tuntech.common.widget.dialog.LocalModalContext
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.iap.IapManager
import com.tuntech.supertvstreamcast.ui.language.rememberAppLanguage
import com.tuntech.supertvstreamcast.ui.SettingsLinks
import com.tuntech.monetization.ad.AdManagerCallback
import com.tuntech.monetization.ad.InterstitialAdManager
import com.tuntech.supertvstreamcast.constant.AdName
import com.tuntech.supertvstreamcast.domain.PremiumFeature
import com.tuntech.supertvstreamcast.domain.parsePremiumSettings
import com.tuntech.supertvstreamcast.ui.PremiumGateDialog
import com.tuntech.supertvstreamcast.ui.rewardName
import com.tuntech.supertvstreamcast.ui.showReward
import com.tuntech.supertvstreamcast.widgets.AppOpenAdGate
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import com.tuntech.supertvstreamcast.ui.app_loading.showAppLoading
import com.tuntech.supertvstreamcast.widgets.dialog.showAlertDialog
import monetization.resources.Res as MonetizationRes
import monetization.resources.premium_purchase_fail_description
import monetization.resources.premium_purchase_fail_title
import monetization.resources.premium_purchase_success_description
import monetization.resources.premium_purchase_success_title
import monetization.resources.premium_restoring_title
import shared.resources.ok
import shared.resources.please_wait_a_moment
import com.tuntech.common.util.PlatformUtil
import com.tuntech.supertvstreamcast.platform.rememberCopyText
import shared.resources.settings_device_id_copied_body
import shared.resources.settings_device_id_copied_title
import shared.resources.settings_logs_cleared_body
import shared.resources.settings_logs_cleared_title
import shared.resources.settings_share_app_message

/**
 * Root of the app after the entry flow: the Home / Remote / Mirror / IPTV / Settings tabs. Runs
 * [AppFlowEffect] (startup paywall, then the in-app update check).
 */
@Composable
fun DashboardScreen(
    isPaywallClosed: Boolean = false,
    navigateToPaywall: (String) -> Unit,
    navigateBack: () -> Unit,
    navigateToLanguage: () -> Unit = {},
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

    val platformContext = com.tuntech.common.util.LocalPlatformContext.current
    val modalContext = LocalModalContext.current
    val adManager = koinInject<AdManager>()

    // Free-tier limits come from Remote Config; reaching one opens the premium gate (paywall or a rewarded ad).
    val limits = remember { parseIptvLimits(runCatching { Firebase.remoteConfig.getString(RemoteConfigKey.IPTV_SETTINGS) }.getOrDefault("")) }
    val premiumSettings = remember { parsePremiumSettings(runCatching { Firebase.remoteConfig.getString(RemoteConfigKey.PREMIUM_SETTINGS) }.getOrDefault("")) }
    LaunchedEffect(isPremium, limits) { iptvModel.access(isPremium, limits) }
    var gate by remember { mutableStateOf<PremiumFeature?>(null) }
    LaunchedEffect(iptv.premiumRequired) {
        iptv.premiumRequired?.let { feature -> iptvModel.clearPremiumRequired(); gate = feature }
    }
    // Remote features locked for free users; a reward opens one until its deadline, then this re-reads the clock.
    val unlocks by model.unlocks.collectAsStateWithLifecycle()
    var clockTick by remember { mutableStateOf(0) }
    val locked = remember(unlocks, isPremium, premiumSettings, clockTick) { unlocks.locked(premiumSettings, isPremium, epochSeconds()) }
    LaunchedEffect(unlocks, clockTick) {
        val next = unlocks.nextExpiry(epochSeconds()) ?: return@LaunchedEffect
        delay((next - epochSeconds()).coerceAtLeast(1) * 1000); clockTick++
    }
    LaunchedEffect(iptv.player != null) { AppOpenAdGate.playing = iptv.player != null }

    /** In-app interstitial: only when one is already loaded, so a tap never waits on an ad request. */
    fun interstitial(name: String, then: () -> Unit) {
        val ready = (adManager.managers[AdManager.INTERSTITIAL_AD] as? InterstitialAdManager)?.isAvailable == true
        if (!ready) { adManager.loadInterstitialAd(); then(); return }
        adManager.showInterstitialAd(context = platformContext, name = name, callback = AdManagerCallback(
            onAdShowedFullScreenContent = modalContext::dismissLoading,
            onAdClosed = { modalContext.dismissLoading(); then() },
        ))
    }
    /** Hands the user to another app or system screen without an app-open ad on the way back. */
    fun leaving(action: () -> Unit) { AppOpenAdGate.skipNextReturn(); action() }
    val reminders = rememberReminderScheduler()
    val scope = rememberCoroutineScope()
    fun sourceType(id: String) = iptv.sources.firstOrNull { it.id == id }?.type

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

    // Taps are logged here as ec_* events with the tab and brand (or the IPTV screen) they came from.
    fun ec(name: String, vararg params: Pair<String, Any?>) = ClickEvent.log(name, "tab" to state.tab, "brand" to state.brand, *params)
    fun iec(name: String, vararg params: Pair<String, Any?>) = ClickEvent.log("iptv_$name", "screen" to iptv.screen.analyticsName(), *params)
    val actions = TvActions(
        back = model::back, brand = { brand -> ec("settings_brand", "selected" to brand); model.brand(brand) },
        // Tab taps are logged where they happen (dock, home cards, header) to keep the entry point.
        tab = { tab -> iptvModel.closePlayer(); model.tab(tab) },
        connection = { open -> ec(if (open) "tv_connect_open" else "tv_connect_close"); model.connection(open) },
        connect = { ip, psk -> ec("tv_connect", "pairing_code" to state.codeRequested); lan { model.connect(ip, psk) } },
        disconnect = { ec("tv_disconnect"); model.disconnect() },
        scan = { ec("tv_scan"); lan(model::scan) }, pick = { device -> ec("tv_pick", "device_brand" to device.brand); lan { model.pick(device) } },
        key = { key -> ec(remoteKeyEvent(key), "key" to key); model.send(key) },
        text = { text -> ec("remote_keyboard_send"); model.sendText(text) },
        move = model::move, click = { ec("remote_touchpad_click"); model.click() },
        loadApps = { ec("remote_apps_load"); model.loadApps() }, launch = { app -> ec("remote_app_launch", "app" to app.title); model.launch(app) },
        share = share?.let { action -> { ec("mirror_share"); AppOpenAdGate.skipNextReturn(); if (!action()) model.report(UiError.MIRROR) } },
        premium = { source -> ec(if (source == IapConstant.IAP_SOURCE_SETTING) "settings_premium" else "header_premium"); navigateToPaywall(source) },
        wake = { ip -> ec("tv_wake"); lan { model.wake(ip) } }, forgetTvs = { ec("settings_forget_tvs"); model.forgetTvs() },
        theme = { mode -> ec("settings_theme", "mode" to mode); themeRepository.select(mode) },
        openFeature = { tab -> interstitial(AdName.FEATURE_OPEN) { iptvModel.closePlayer(); model.tab(tab) } },
        locked = { feature -> ec("premium_gate", "feature" to feature); gate = feature },
    )
    // LAN-hosted playlists, Xtream panels and streams ask for local-network access first.
    val iptvActions = IptvActions(
        premium = { iec("premium"); navigateToPaywall(IapConstant.IAP_SOURCE_HEADER) },
        back = { iec("back"); iptvModel.back() }, clearError = iptvModel::clearError, clearNotice = iptvModel::clearNotice,
        cancelImport = { iec("import_cancel"); iptvModel.cancelImport() },
        open = { id -> iec("source_open", "type" to sourceType(id)); iptvLan(sourceUrl(id)) { interstitial(AdName.IPTV_SOURCE_OPEN) { iptvModel.open(id) } } },
        refresh = { id -> iec("source_refresh", "type" to sourceType(id)); iptvLan(sourceUrl(id)) { iptvModel.refresh(id) } },
        rename = { id, name -> iec("source_rename"); iptvModel.rename(id, name) },
        refreshEvery = { id, hours -> iec("source_auto_refresh", "hours" to hours); iptvModel.refreshEvery(id, hours) },
        delete = { id -> iec("source_delete", "type" to sourceType(id)); iptvModel.delete(id) },
        share = { source -> iec("source_share", "type" to source.type); source.shareUrl?.let { url -> AppOpenAdGate.skipNextReturn(); context.shareText("$shareMessage\n${buildImportLink(source.name, url)}\n$url") } },
        addPlaylist = { input, name ->
            val remote = isRemoteSource(input)
            iec("add_playlist", "input" to if (remote) "link" else "content")
            if (remote) iptvLan(input) { iptvModel.addPlaylist(input, name) } else iptvModel.addPlaylist(input, name)
        },
        pickPlaylistFile = { iec("add_playlist_file"); leaving(pickPlaylist) },
        addXtream = { server, user, pass, name -> iec("add_xtream"); iptvLan(XtreamApi.normalizeServer(server).orEmpty()) { iptvModel.addXtream(server, user, pass, name) } },
        addStream = { url, title -> iec("add_stream"); iptvLan(url) { iptvModel.addStream(url, title) } },
        acceptImport = { iec("import_accept"); iptvLan(iptv.pendingImport?.url.orEmpty(), iptvModel::acceptImport) },
        dismissImport = { iec("import_dismiss"); iptvModel.dismissImport() },
        importGuide = { url -> iec("guide_import"); iptvLan(url) { iptvModel.importGuide(url) } }, pickGuideFile = { iec("guide_file"); leaving(pickGuide) },
        play = { channel, queue -> iec("play", "kind" to channel.kind); iptvLan(channel.url) { iptvModel.play(channel, queue) } },
        zap = { step -> iec(if (step > 0) "player_next" else "player_previous"); iptvModel.zap(step) },
        favorite = { channel -> iec("favorite", "on" to (channel.url !in iptv.favorites), "kind" to channel.kind); iptvModel.favorite(channel) },
        hide = { channel, hidden -> iec(if (hidden) "hide" else "unhide", "kind" to channel.kind); iptvModel.hide(channel, hidden) },
        openLibrary = { list -> iec("library_open", "list" to list); iptvModel.openLibrary(list) },
        clear = { list -> iec("library_clear", "list" to list); iptvModel.clear(list) },
        openProgrammes = { channel -> iec("programmes_open"); iptvModel.openProgrammes(channel) },
        playEpisode = { episode -> iec("episode_play"); iptvLan(episode.url) { iptvModel.playEpisode(episode) } },
        retrySeries = { series -> iec("series_retry"); iptvModel.openSeries(series) },
        submitPasscode = { code -> iec("passcode_submit"); iptvModel.submitPasscode(code) }, changePasscode = { iec("passcode_change"); iptvModel.changePasscode() },
        closePlayer = { iec("player_close"); iptvModel.closePlayer(); interstitial(AdName.IPTV_PLAYER_CLOSE) {} }, expand = { iec("player_expand"); iptvModel.expand() },
        openGuide = { iec("guide_open"); iptvModel.openGuide() }, openScanner = { iec("qr_scanner_open"); iptvModel.openScanner() },
        scanned = { text -> iec("qr_scanned"); iptvModel.scanned(text) },
        remind = { channel, programme, minutes ->
            iec("reminder_set", "minutes" to minutes)
            scope.launch {
                val reminder = Reminder(reminderId(channel.url, programme.start), channel.url, channel.title, programme.start, programme.title, programme.start - minutes * 60L)
                val body = org.jetbrains.compose.resources.getString(Res.string.iptv_reminder_body, channel.title, formatClock(programme.start))
                if (reminders.schedule(reminder, programme.title, body)) iptvModel.addReminder(reminder) else iptvModel.reminderDenied()
            }
        },
        unremind = { id -> iec("reminder_remove"); reminders.cancel(id); iptvModel.removeReminder(id) },
        progress = iptvModel::progress, ended = iptvModel::ended, playerError = { iptvModel.report(UiError.PLAYER) }, started = iptvModel::started,
    )
    // Settings → general / support / legal / troubleshooting rows (same set as AirPrint and RetroOne).
    val uriHandler = LocalUriHandler.current
    val iapManager = koinInject<IapManager>()
    val termsUrl = rememberRemoteConfigString(RemoteConfigKey.TERM_OF_SERVICE_URL)
    val privacyUrl = rememberRemoteConfigString(RemoteConfigKey.PRIVACY_POLICY_URL)
    val supportEmail = rememberRemoteConfigString(RemoteConfigKey.SUPPORT_EMAIL).trim()
    val language = rememberAppLanguage()
    val appName = remember(platformContext) { platformContext.getAppName() }
    val version = remember(platformContext) { "${BuildConfig.getVersionName(platformContext)} (${BuildConfig.getVersionCode(platformContext)})" }
    val shareAppMessage = stringResource(Res.string.settings_share_app_message, platformContext.getStoreUrl())
    val copyText = rememberCopyText()
    fun openUrl(url: String) { AppOpenAdGate.skipNextReturn(); runCatching { uriHandler.openUri(url) } }
    fun restorePurchase() {
        modalContext.showAppLoading(title = MonetizationRes.string.premium_restoring_title, description = Res.string.please_wait_a_moment)
        scope.launch {
            runCatching { iapManager.restorePurchase() }
            modalContext.dismissLoading()
            if (iapManager.isActive.value) {
                adManager.deactivate()
                adManager.clean()
                modalContext.showAlertDialog(title = MonetizationRes.string.premium_purchase_success_title,
                    description = MonetizationRes.string.premium_purchase_success_description, positiveAction = Res.string.ok)
            } else modalContext.showAlertDialog(title = MonetizationRes.string.premium_purchase_fail_title,
                description = MonetizationRes.string.premium_purchase_fail_description, positiveAction = Res.string.ok)
        }
    }
    val settings = SettingsLinks(
        language = language, openLanguage = { ec("settings_language", "current" to language.code); navigateToLanguage() },
        version = version,
        rate = { ec("settings_rate"); leaving { platformContext.openStoreDetails() } },
        shareApp = { ec("settings_share_app"); leaving { platformContext.shareText(shareAppMessage) } },
        contact = supportEmail.takeIf { it.isNotEmpty() }?.let { email ->
            { ec("settings_contact"); openUrl("mailto:$email?subject=" + "$appName $version".replace(" ", "%20")) }
        },
        restore = { ec("settings_restore"); restorePurchase() },
        manageSubscription = if (isPremium) ({ ec("settings_manage_subscription"); leaving { platformContext.openStoreSubscription() } }) else null,
        terms = termsUrl.takeIf { it.isNotBlank() }?.let { url -> { ec("settings_terms"); openUrl(url) } },
        privacy = privacyUrl.takeIf { it.isNotBlank() }?.let { url -> { ec("settings_privacy"); openUrl(url) } },
        shareLogs = { ec("settings_share_logs"); AppOpenAdGate.skipNextReturn(); scope.launch { AppLogger.sendLogsInEmail(platformContext, listOf(supportEmail).filter { it.isNotEmpty() }, "") } },
        copyDeviceId = {
            ec("settings_copy_device_id")
            PlatformUtil.getDeviceId(platformContext)?.let { id ->
                copyText(id)
                modalContext.showAlertDialog(title = Res.string.settings_device_id_copied_title,
                    description = Res.string.settings_device_id_copied_body, positiveAction = Res.string.ok)
            }
        },
        cleanLogs = {
            ec("settings_clean_logs")
            scope.launch {
                AppLogger.cleanLogFiles(platformContext)
                modalContext.showAlertDialog(title = Res.string.settings_logs_cleared_title,
                    description = Res.string.settings_logs_cleared_body, positiveAction = Res.string.ok)
            }
        },
    )
    TvScaffold(state, actions, iptv, iptvActions, settings = settings, locked = locked)

    gate?.let { feature ->
        val rewardAvailable = adManager.isReady.value && adManager.checkEnabled(AdManager.REWARDED_AD, feature.rewardName())
        PremiumGateDialog(feature, premiumSettings, rewardAvailable,
            onUpgrade = {
                ClickEvent.log("premium_gate_upgrade", "feature" to feature); gate = null
                navigateToPaywall(if (feature.isRemote) IapConstant.IAP_SOURCE_REMOTE_LOCK else IapConstant.IAP_SOURCE_IPTV_LIMIT)
            },
            onWatchAd = {
                ClickEvent.log("premium_gate_reward", "feature" to feature); gate = null
                adManager.showReward(platformContext, modalContext, feature) {
                    ClickEvent.log("premium_gate_rewarded", "feature" to feature)
                    when (feature) {
                        PremiumFeature.IPTV_WATCH_TIME -> iptvModel.grantWatchTime(premiumSettings.rewardWatchSeconds)
                        PremiumFeature.IPTV_SOURCES -> iptvModel.grantSources(premiumSettings.rewardSources)
                        else -> model.unlock(feature, premiumSettings.rewardUnlockMinutes, epochSeconds())
                    }
                }
            },
            onDismiss = { gate = null })
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun epochSeconds(): Long = kotlin.time.Clock.System.now().epochSeconds

/** Remote keys as `remote_<key>`; the ten digits share `remote_number` (the digit is the `key` parameter). */
private fun remoteKeyEvent(key: RemoteKey) = if (key.name.startsWith("NUM_")) "remote_number" else "remote_${key.name}"

/** Stable names for analytics; class names are obfuscated in release builds. */
private fun IptvScreen.analyticsName() = when (this) {
    IptvScreen.Hub -> "hub"; IptvScreen.Source -> "source"; is IptvScreen.Library -> "library"; is IptvScreen.Series -> "series"
    is IptvScreen.Programmes -> "programmes"; is IptvScreen.Passcode -> "passcode"; IptvScreen.Guide -> "guide"; IptvScreen.Scanner -> "scanner"
}
