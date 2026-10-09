package com.tuntech.supertvstreamcast.ui.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuntech.supertvstreamcast.data.*
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.ui.UiError
import com.tuntech.supertvstreamcast.ui.epochSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface IptvScreen {
    data object Hub : IptvScreen
    /** Channels of [IptvUiState.active]. */
    data object Source : IptvScreen
    data class Library(val list: LibraryList) : IptvScreen
    data class Series(val series: Channel) : IptvScreen
    /** Upcoming programmes of one channel from the loaded guide. */
    data class Programmes(val channel: Channel) : IptvScreen
    data class Passcode(val flow: PasscodeFlow) : IptvScreen
    /** Day guide of the active source: every channel with programmes, as a list or a time grid. */
    data object Guide : IptvScreen
    data object Scanner : IptvScreen
}
/** [queue] is the list the channel was opened from, for previous/next; [autoNext] continues to the next episode when one ends. */
data class PlayerState(val channel: Channel, val sourceId: String, val queue: List<Channel>, val startMs: Long = 0, val autoNext: Boolean = false,
    /** Shown as a small floating player over the IPTV screens. */
    val minimized: Boolean = false)
data class SeriesState(val series: Channel, val info: SeriesInfo? = null, val failed: Boolean = false)
enum class IptvNotice { ADDED, REFRESHED, REFRESH_FAILED, DELETED, SAVED, HIDDEN, UNHIDDEN, PASSCODE_SET, REMINDER_SET, REMINDER_REMOVED, REMINDER_DENIED, QR_INVALID }

data class IptvUiState(
    /** False until the saved library has been read from disk. */
    val loaded: Boolean = false,
    val sources: List<IptvSource> = emptyList(), val entries: List<LibraryEntry> = emptyList(),
    val stack: List<IptvScreen> = listOf(IptvScreen.Hub),
    val activeId: String = "", val channels: List<Channel> = emptyList(), val catalogLoading: Boolean = false,
    val player: PlayerState? = null, val series: SeriesState? = null,
    /** EPG of the active source keyed by channel URL; session only. */
    val guide: Map<String, List<Programme>> = emptyMap(), val guideLoading: Boolean = false,
    val busy: Boolean = false, val error: UiError? = null, val notice: IptvNotice? = null,
    /** An import that can be cancelled is running; [importCount] is the number of channels read so far. */
    val importing: Boolean = false, val importCount: Int = 0,
    /** Grows with every finished import so open dialogs know to close. */
    val imported: Int = 0,
    val passcodeSet: Boolean = false,
    /** A playlist offered by a link from outside the app, waiting for the user to accept it. */
    val pendingImport: ImportRequest? = null,
    val favorites: Set<String> = emptySet(), val hidden: Set<String> = emptySet(),
    val reminders: List<Reminder> = emptyList(),
    /** A free-tier limit was reached; the host opens the paywall and calls [IptvViewModel.clearPremiumRequired]. */
    val premiumRequired: Boolean = false,
) {
    val screen get() = stack.last()
    val active get() = sources.firstOrNull { it.id == activeId }
    val canGoBack get() = player?.minimized == false || stack.size > 1
    fun reminder(channelUrl: String, start: Long) = reminders.firstOrNull { it.id == reminderId(channelUrl, start) }
    fun entry(url: String) = entries.firstOrNull { it.channel.url == url }
    internal fun withEntries(entries: List<LibraryEntry>) = copy(entries = entries,
        favorites = entries.filter { it.favorite }.mapTo(HashSet()) { it.channel.url }, hidden = entries.filter { it.hidden }.mapTo(HashSet()) { it.channel.url })
}

/**
 * The IPTV library: saved sources (playlists, files, Xtream accounts, single streams), the active
 * source's channels, favorites / recents / hidden channels, the guide and the player. Sources and
 * user state are kept on the device by [IptvStore]; the guide lives for the session.
 */
class IptvViewModel(
    private val store: IptvStore,
    private val repository: TvRepository = TvRepository(),
    private val now: () -> Long = ::epochSeconds,
) : ViewModel() {
    private val mutable = MutableStateFlow(IptvUiState())
    val state: StateFlow<IptvUiState> = mutable.asStateFlow()
    private var passcodeHash = ""
    private var passcodeSalt = ""
    private var importJob: Job? = null
    private var catalogJob: Job? = null
    private var guideJob: Job? = null
    private var seriesJob: Job? = null
    /** Sources whose provider guide was already tried this session. */
    private val guideTried = HashSet<String>()
    private var progressSavedAt = 0L
    private var premium = false
    private var limits = IptvLimits()
    private var watchDay = 0L
    private var watchSeconds = 0
    private var watchJob: Job? = null

    init {
        viewModelScope.launch {
            val index = store.loadIndex()
            passcodeHash = index.passcodeHash; passcodeSalt = index.passcodeSalt
            watchDay = index.watchDay; watchSeconds = index.watchSeconds
            mutable.update { it.copy(loaded = true, sources = index.sources, passcodeSet = index.passcodeHash.isNotEmpty(),
                reminders = index.reminders.filter { r -> r.start > now() }).withEntries(index.entries) }
        }
    }

    // Navigation

    /** Back from the full player keeps it playing as a mini player; its own close button stops it. */
    fun back() {
        if (state.value.player?.minimized == false) { minimize(); return }
        mutable.update { if (it.stack.size > 1) it.copy(stack = it.stack.dropLast(1), error = null, series = if (it.screen is IptvScreen.Series) null else it.series) else it }
    }
    fun home() { closePlayer(); mutable.update { it.copy(stack = listOf(IptvScreen.Hub), error = null, series = null) } }
    fun clearError() = mutable.update { it.copy(error = null) }
    fun clearNotice() = mutable.update { it.copy(notice = null) }
    fun report(error: UiError) = mutable.update { it.copy(error = error) }
    private fun push(screen: IptvScreen) = mutable.update { it.copy(stack = it.stack + screen, error = null) }
    fun openLibrary(list: LibraryList) {
        if (list == LibraryList.HIDDEN) push(IptvScreen.Passcode(PasscodeFlow.start(PasscodePurpose.OPEN_HIDDEN, state.value.passcodeSet)))
        else push(IptvScreen.Library(list))
    }
    fun openProgrammes(channel: Channel) { minimize(); push(IptvScreen.Programmes(channel)) }
    fun openGuide() = push(IptvScreen.Guide)
    fun openScanner() = push(IptvScreen.Scanner)
    /** Text read from a QR code: the app's import link or a plain playlist link. */
    fun scanned(text: String) {
        val request = parseImportLink(text)
        mutable.update { it.copy(stack = it.stack.filterNot { s -> s is IptvScreen.Scanner }, pendingImport = request, notice = if (request == null) IptvNotice.QR_INVALID else it.notice) }
    }

    // Free tier

    /** Entitlement and Remote Config limits, pushed by the host whenever they change. */
    fun access(premium: Boolean, limits: IptvLimits) { this.premium = premium; this.limits = limits }
    fun clearPremiumRequired() = mutable.update { it.copy(premiumRequired = false) }
    private fun requirePremium() = mutable.update { it.copy(premiumRequired = true) }
    private fun sourceBlocked(): Boolean = (!limits.canAddSource(state.value.sources.size, premium)).also { if (it) requirePremium() }
    private fun watchedToday(): Int { val day = now() / 86_400; if (day != watchDay) { watchDay = day; watchSeconds = 0 }; return watchSeconds }
    /** Counts playback time while something plays and stops it at the free daily limit. */
    private fun countWatchTime() {
        if (watchJob?.isActive == true) return
        watchJob = viewModelScope.launch {
            while (state.value.player != null) {
                kotlinx.coroutines.delay(WATCH_TICK_SECONDS * 1000L)
                if (state.value.player == null) break
                watchSeconds = watchedToday() + WATCH_TICK_SECONDS
                if (!limits.canWatch(watchSeconds, premium)) { closePlayer(); requirePremium() }
            }
            persist()
        }
    }

    // Reminders

    fun addReminder(reminder: Reminder) {
        mutable.update { it.copy(reminders = it.reminders.filter { r -> r.id != reminder.id && r.start > now() } + reminder, notice = IptvNotice.REMINDER_SET) }
        persist()
    }
    fun removeReminder(id: Int) {
        mutable.update { it.copy(reminders = it.reminders.filter { r -> r.id != id }, notice = IptvNotice.REMINDER_REMOVED) }
        persist()
    }
    fun reminderDenied() = mutable.update { it.copy(notice = IptvNotice.REMINDER_DENIED) }

    // Sources

    /** Opens a saved source, reading its channels from disk and re-syncing first when that is due. */
    fun open(sourceId: String) {
        val source = state.value.sources.firstOrNull { it.id == sourceId } ?: return
        if (state.value.activeId == sourceId && state.value.channels.isNotEmpty()) {
            mutable.update { it.copy(stack = listOf(IptvScreen.Hub, IptvScreen.Source), error = null) }
            loadProviderGuide()
            return
        }
        catalogJob?.cancel(); guideJob?.cancel()
        mutable.update { it.copy(activeId = sourceId, channels = emptyList(), catalogLoading = true, guide = emptyMap(), guideLoading = false,
            stack = listOf(IptvScreen.Hub, IptvScreen.Source), error = null) }
        catalogJob = viewModelScope.launch {
            val saved = store.loadCatalog(sourceId)
            mutable.update { if (it.activeId == sourceId) it.copy(channels = saved.orEmpty(), catalogLoading = false) else it }
            if (source.refreshable && (saved == null || isRefreshDue(source, now()))) refresh(sourceId, quiet = saved != null)
            else loadProviderGuide()
        }
    }
    /** Downloads the source again. Favorites, recents and hidden channels are kept. */
    fun refresh(sourceId: String, quiet: Boolean = false) {
        val source = state.value.sources.firstOrNull { it.id == sourceId } ?: return
        if (!source.refreshable) return
        import(if (source.type == SourceType.XTREAM) UiError.XTREAM else UiError.PLAYLIST, quiet) {
            val library = source.login?.let { repository.loadXtream(it, ::progress) } ?: repository.loadPlaylist(source.url, ::progress)
            save(source, library, IptvNotice.REFRESHED, open = false)
        }
    }
    fun rename(sourceId: String, name: String) = edit(sourceId) { it.copy(name = name.trim().take(60)) }
    fun refreshEvery(sourceId: String, hours: Int) = edit(sourceId) { it.copy(refreshHours = hours.coerceAtLeast(0)) }
    private fun edit(sourceId: String, change: (IptvSource) -> IptvSource) {
        mutable.update { it.copy(sources = it.sources.map { s -> if (s.id == sourceId) change(s) else s }, notice = IptvNotice.SAVED) }
        persist()
    }
    fun delete(sourceId: String) {
        if (state.value.busy) return
        if (state.value.player?.sourceId == sourceId) closePlayer()
        if (state.value.activeId == sourceId) { catalogJob?.cancel(); guideJob?.cancel() }
        mutable.update {
            val active = it.activeId == sourceId
            it.copy(sources = it.sources.filter { s -> s.id != sourceId }, notice = IptvNotice.DELETED,
                activeId = if (active) "" else it.activeId, channels = if (active) emptyList() else it.channels,
                guide = if (active) emptyMap() else it.guide, catalogLoading = if (active) false else it.catalogLoading,
                stack = if (active) listOf(IptvScreen.Hub) else it.stack, series = if (active) null else it.series,
            ).withEntries(it.entries.withoutSource(sourceId))
        }
        persist()
        viewModelScope.launch { store.deleteCatalog(sourceId) }
    }

    // Adding sources

    /**
     * Playlist URL (HTTP/HTTPS) or pasted M3U content. A provider `get.php` link becomes an Xtream
     * account when its login is accepted; a link to one HLS stream is saved as a single stream.
     */
    fun addPlaylist(value: String, name: String = "") {
        if (sourceBlocked()) return
        val trimmed = value.trim()
        val remote = isRemoteSource(trimmed)
        import(UiError.PLAYLIST) {
            val account = if (remote && "get.php" in trimmed.substringBefore('?')) XtreamApi.credentials(trimmed) else null
            val xtream = account?.let { try { repository.loadXtream(it, ::progress) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } }
            if (account != null && xtream != null) {
                save(IptvSource(newId(), SourceType.XTREAM, name.ifBlank { account.server.substringAfter("://") }, account.server, account.username, account.password), xtream)
                return@import
            }
            try {
                val library = repository.loadPlaylist(value, ::progress)
                if (remote) save(IptvSource(newId(), SourceType.PLAYLIST, name.ifBlank { defaultSourceName(trimmed) }, trimmed), library)
                else save(IptvSource(newId(), SourceType.FILE, name), library)
            } catch (_: HlsStreamException) {
                saveStream(singleStream(trimmed, name) ?: throw NotPlaylistException())
            }
        }
    }
    /** Bytes picked from device storage; null means the file could not be read. */
    fun addPlaylistFile(bytes: ByteArray?) {
        if (bytes == null || bytes.size > PLAYLIST_MAX_BYTES) { report(UiError.FILE); return }
        if (sourceBlocked()) return
        import(UiError.PLAYLIST) {
            save(IptvSource(newId(), SourceType.FILE, ""), withContext(Dispatchers.Default) { parsePlaylistBytes(bytes, ::progress) })
        }
    }
    fun addXtream(server: String, username: String, password: String, name: String = "") {
        val login = XtreamApi.login(server, username, password)
        if (login == null) { report(UiError.XTREAM_INPUT); return }
        if (sourceBlocked()) return
        import(UiError.XTREAM) {
            save(IptvSource(newId(), SourceType.XTREAM, name.ifBlank { login.server.substringAfter("://") }, login.server, login.username, login.password),
                repository.loadXtream(login, ::progress))
        }
    }
    /** Saves one user-entered stream as its own source and plays it. */
    fun addStream(url: String, title: String) {
        val channel = singleStream(url, title)
        if (channel == null) { report(UiError.STREAM_URL); return }
        if (sourceBlocked()) return
        import(UiError.STREAM_URL) { saveStream(channel) }
    }
    /** A link opened from outside the app. Nothing is downloaded until the user accepts it. */
    fun offerImport(link: String) {
        closePlayer()
        val request = parseImportLink(link)
        mutable.update { it.copy(pendingImport = request, error = if (request == null) UiError.PLAYLIST else null, stack = listOf(IptvScreen.Hub), series = null) }
    }
    fun dismissImport() = mutable.update { it.copy(pendingImport = null) }
    fun acceptImport() {
        val request = state.value.pendingImport ?: return
        dismissImport()
        addPlaylist(request.url, request.name)
    }
    /** Stops a running import or refresh; the library is left as it was. */
    fun cancelImport() { importJob?.cancel() }

    private suspend fun saveStream(channel: Channel) {
        val source = save(IptvSource(newId(), SourceType.STREAM, channel.title, channel.url), IptvLibrary(listOf(channel)), open = false)
        play(channel, listOf(channel), source.id)
    }
    /** Stores the downloaded channels. A source with the same link and login is updated in place. */
    private suspend fun save(candidate: IptvSource, library: IptvLibrary, notice: IptvNotice = IptvNotice.ADDED, open: Boolean = true): IptvSource {
        val existing = state.value.sources.firstOrNull { it.id == candidate.id }
            ?: state.value.sources.firstOrNull { candidate.url.isNotEmpty() && it.sameOrigin(candidate) }
        val base = existing ?: candidate.copy(addedAt = now())
        val source = base.copy(syncedAt = now(), channelCount = library.channels.size, guideUrl = library.guideUrl, truncated = library.truncated,
            account = library.account ?: base.account)
        store.saveCatalog(source.id, library.channels)
        if (existing == null || open) { catalogJob?.cancel(); guideJob?.cancel() }
        mutable.update {
            val sources = if (existing == null) it.sources + source else it.sources.map { s -> if (s.id == source.id) source else s }
            val shown = open || it.activeId == source.id
            it.copy(sources = sources, notice = notice, imported = it.imported + 1,
                activeId = if (open) source.id else it.activeId, channels = if (shown) library.channels else it.channels,
                catalogLoading = if (shown) false else it.catalogLoading,
                guide = if (open && it.activeId != source.id) emptyMap() else it.guide,
                stack = if (open) listOf(IptvScreen.Hub, IptvScreen.Source) else it.stack)
        }
        persist()
        if (state.value.activeId == source.id) loadProviderGuide()
        return source
    }

    // Library

    fun favorite(channel: Channel) = entries { it.toggleFavorite(channel, owner(channel)) }
    /** Hidden channels leave every list except the passcode-protected one. */
    fun hide(channel: Channel, hidden: Boolean) {
        val owner = owner(channel)
        if (hidden && state.value.player?.channel?.url == channel.url) closePlayer()
        mutable.update { it.copy(notice = if (hidden) IptvNotice.HIDDEN else IptvNotice.UNHIDDEN) }
        entries { it.setHidden(channel, owner, hidden) }
    }
    fun clear(list: LibraryList) = entries { it.cleared(list) }
    private fun entries(change: (List<LibraryEntry>) -> List<LibraryEntry>) {
        mutable.update { it.withEntries(change(it.entries)) }
        persist()
    }
    /** The source a channel belongs to: where it was saved from, else the one being played or browsed. */
    private fun owner(channel: Channel): String {
        val current = state.value
        return current.entry(channel.url)?.sourceId ?: current.player?.takeIf { it.channel.url == channel.url }?.sourceId ?: current.activeId
    }

    fun submitPasscode(pin: String) {
        val screen = state.value.screen as? IptvScreen.Passcode ?: return
        val replaceTop = { next: IptvScreen -> mutable.update { it.copy(stack = it.stack.dropLast(1) + next) } }
        when (val result = screen.flow.submit(pin) { passcodeHash.isNotEmpty() && passcodeHash(it, passcodeSalt) == passcodeHash }) {
            is PasscodeResult.Next -> replaceTop(IptvScreen.Passcode(result.flow))
            PasscodeResult.Verified -> replaceTop(IptvScreen.Library(LibraryList.HIDDEN))
            is PasscodeResult.Created -> {
                passcodeSalt = kotlin.random.Random.nextLong().toString(16)
                passcodeHash = passcodeHash(result.pin, passcodeSalt)
                mutable.update { it.copy(passcodeSet = true, notice = IptvNotice.PASSCODE_SET) }
                persist()
                if (screen.flow.purpose == PasscodePurpose.OPEN_HIDDEN) replaceTop(IptvScreen.Library(LibraryList.HIDDEN)) else back()
            }
        }
    }
    fun changePasscode() = push(IptvScreen.Passcode(PasscodeFlow.start(PasscodePurpose.CHANGE, state.value.passcodeSet)))

    // Playback

    /** Plays [channel]; [queue] is the list it was picked from. A series opens its episode list instead. */
    fun play(channel: Channel, queue: List<Channel>, sourceId: String = owner(channel), autoNext: Boolean = false) {
        if (channel.kind == ContentKind.SERIES) { openSeries(channel, sourceId); return }
        if (!limits.canWatch(watchedToday(), premium)) { closePlayer(); requirePremium(); return }
        saveProgress()
        mutable.update {
            val start = if (channel.kind == ContentKind.LIVE) 0 else resumePosition(it.entry(channel.url))
            it.copy(player = PlayerState(channel, sourceId, queue.filter { c -> c.kind != ContentKind.SERIES }, start, autoNext), error = null)
                .withEntries(it.entries.watched(channel, sourceId, now()))
        }
        persist()
        countWatchTime()
    }
    fun zap(step: Int) {
        val player = state.value.player ?: return
        if (player.queue.none { it.url == player.channel.url }) return
        adjacentChannel(player.queue, player.channel, step)?.let { play(it, player.queue, player.sourceId, player.autoNext) }
    }
    /** Position of the video being played, reported by the player every few seconds. */
    fun progress(url: String, positionMs: Long, durationMs: Long) {
        if (state.value.player?.channel?.let { it.url == url && it.kind != ContentKind.LIVE } != true) return
        mutable.update { it.withEntries(it.entries.progress(url, positionMs, durationMs)) }
        if (now() - progressSavedAt >= PROGRESS_SAVE_SECONDS) saveProgress()
    }
    /** The video reached its end: continue with the next episode, if any. */
    fun ended() {
        val player = state.value.player ?: return
        mutable.update { it.withEntries(it.entries.progress(player.channel.url, 0, 0)) }
        val index = player.queue.indexOfFirst { it.url == player.channel.url }
        if (player.autoNext && index >= 0 && index < player.queue.lastIndex) play(player.queue[index + 1], player.queue, player.sourceId, true)
        else saveProgress()
    }
    fun closePlayer() {
        if (state.value.player == null) return
        mutable.update { it.copy(player = null, error = null) }
        saveProgress()
    }
    private fun saveProgress() { progressSavedAt = now(); persist() }
    fun minimize() = mutable.update { it.copy(player = it.player?.copy(minimized = true), error = null) }
    fun expand() = mutable.update { it.copy(player = it.player?.copy(minimized = false), error = null) }

    fun openSeries(series: Channel, sourceId: String = owner(series)) {
        val login = state.value.sources.firstOrNull { it.id == sourceId }?.login ?: return
        seriesJob?.cancel()
        mutable.update { it.copy(series = SeriesState(series), stack = it.stack.filterNot { s -> s is IptvScreen.Series } + IptvScreen.Series(series), error = null) }
        seriesJob = viewModelScope.launch {
            val info = try { repository.loadSeries(login, series) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            mutable.update { if (it.series?.series?.url == series.url) it.copy(series = SeriesState(series, info, failed = info == null)) else it }
        }
    }
    fun playEpisode(episode: Episode) {
        val series = state.value.series ?: return
        val queue = series.info?.episodes.orEmpty().map { it.toChannel(series.series) }
        queue.firstOrNull { it.url == episode.url }?.let { play(it, queue, owner(series.series), autoNext = true) }
    }

    // Guide

    fun importGuide(url: String) {
        val trimmed = url.trim()
        if (!isStreamUrl(trimmed)) { report(UiError.GUIDE); return }
        guide { channels -> repository.loadGuide(trimmed, channels, now()) }
    }
    fun importGuideFile(bytes: ByteArray?) {
        if (bytes == null || bytes.size > GUIDE_FILE_MAX_BYTES) { report(UiError.FILE); return }
        guide { channels -> withContext(Dispatchers.Default) { parseGuideBytes(bytes, channels, now()) } }
    }
    private fun guide(load: suspend (List<Channel>) -> Map<String, List<Programme>>) {
        val sourceId = state.value.activeId
        val channels = state.value.channels.filter { it.kind == ContentKind.LIVE }
        if (channels.isEmpty()) { report(UiError.GUIDE_NO_CHANNELS); return }
        guideJob?.cancel()
        import(UiError.GUIDE) {
            val guide = load(channels)
            mutable.update { if (it.activeId == sourceId) it.copy(guide = guide, guideLoading = false, imported = it.imported + 1) else it }
        }
    }
    /** Loads the guide the provider advertises, once per session and source, without blocking the screen; failures stay silent. */
    private fun loadProviderGuide() {
        val source = state.value.active ?: return
        val channels = state.value.channels.filter { it.kind == ContentKind.LIVE }
        if (source.guideUrl.isEmpty() || channels.isEmpty() || state.value.guide.isNotEmpty() || !guideTried.add(source.id)) return
        mutable.update { it.copy(guideLoading = true) }
        guideJob = viewModelScope.launch {
            val guide = try { repository.loadGuide(source.guideUrl, channels, now()) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            mutable.update { if (it.activeId == source.id) it.copy(guide = guide ?: it.guide, guideLoading = false) else it }
        }
        guideJob?.invokeOnCompletion { mutable.update { it.copy(guideLoading = false) } }
    }

    private fun progress(count: Int) = mutable.update { it.copy(importCount = count) }
    /**
     * Cancellable download. A server that cannot be reached is reported apart from an unusable body.
     * A [quiet] run is an automatic re-sync: its failure is a notice, since the saved channels still work.
     */
    private fun import(error: UiError, quiet: Boolean = false, action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, importing = true, importCount = 0, error = null) }
        importJob = viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val shown = when (e) { is DownloadException -> UiError.DOWNLOAD; is XtreamAuthException -> UiError.XTREAM_AUTH; else -> error }
                mutable.update { if (quiet) it.copy(notice = IptvNotice.REFRESH_FAILED) else it.copy(error = shown) }
            }
        }
        importJob?.invokeOnCompletion { mutable.update { it.copy(busy = false, importing = false) } }
    }
    private fun persist() {
        val current = state.value
        if (!current.loaded) return
        val index = IptvIndex(current.sources, current.entries, passcodeHash, passcodeSalt, current.reminders, watchDay, watchSeconds)
        viewModelScope.launch { try { store.saveIndex(index) } catch (e: CancellationException) { throw e } catch (_: Exception) {} }
    }
    private fun newId() = "s${now()}x${kotlin.random.Random.nextInt(1000, 9999)}"

    override fun onCleared() { repository.close() }

    private companion object { const val PROGRESS_SAVE_SECONDS = 15; const val WATCH_TICK_SECONDS = 10 }
}
