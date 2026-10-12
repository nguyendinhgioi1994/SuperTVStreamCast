package com.tuntech.supertvstreamcast.ui.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuntech.supertvstreamcast.analytics.AppEvent
import com.tuntech.supertvstreamcast.analytics.EventSink
import com.tuntech.supertvstreamcast.analytics.log
import com.tuntech.supertvstreamcast.data.cast.CastController
import com.tuntech.supertvstreamcast.data.cast.LocalMedia
import com.tuntech.supertvstreamcast.data.cast.ReceiverSource
import com.tuntech.supertvstreamcast.domain.cast.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Something to cast: a picked file (served from the phone) or a link the TV fetches itself. */
class CastItem(val title: String, val kind: MediaKind, val mime: String, val local: LocalMedia? = null, val url: String? = null)

enum class CastError { NO_WIFI, NO_RECEIVER, CONNECT, LOAD, BAD_LINK, CONTROL }

data class CastUiState(
    val scanning: Boolean = false,
    /** A scan has finished at least once, so an empty list means "nothing found". */
    val scanned: Boolean = false,
    val receivers: List<CastReceiver> = emptyList(),
    val selected: CastReceiver? = null,
    val connecting: Boolean = false,
    /** Media picked before a receiver was chosen; sent as soon as one is. */
    val waitingForReceiver: Boolean = false,
    val queue: List<CastItem> = emptyList(),
    val index: Int = 0,
    /** True once the receiver accepted the current item; never set before it answered. */
    val casting: Boolean = false,
    val sending: Boolean = false,
    val status: CastStatus = CastStatus(),
    val slideshow: Boolean = true,
    val error: CastError? = null,
) {
    val current: CastItem? get() = queue.getOrNull(index)
}

/**
 * Cast tab: finds DLNA renderers (and Chromecasts on Android), keeps one session and sends picked
 * photos / videos / music or a pasted link. The phone serves picked files with [share]; the TV
 * fetches them over the Wi-Fi. Status comes from polling the receiver.
 */
class CastViewModel(
    private val sources: List<ReceiverSource>,
    private val share: suspend (LocalMedia) -> String?,
    private val stopSharing: suspend () -> Unit,
    private val events: EventSink = AppEvent::log,
    private val pollMs: Long = 1_000,
    private val slideMs: Long = 5_000,
) : ViewModel() {
    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()
    private var controller: CastController? = null
    private var scanJob: Job? = null
    private var pollJob: Job? = null
    private var sendJob: Job? = null

    /** Looks for receivers for [seconds]; started from a user action only. */
    fun scan(seconds: Int = 8) {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, error = null) }
        scanJob = viewModelScope.launch {
            val lists = sources.associate { it.kind to emptyList<CastReceiver>() }.toMutableMap()
            val job = launch {
                sources.map { source -> source.discover().map { source.kind to it }.catch { } }.merge().collect { (kind, found) ->
                    lists[kind] = found
                    val all = lists.values.flatten()
                    _state.update { it.copy(receivers = all) }
                }
            }
            delay(seconds * 1_000L)
            job.cancel()
            _state.update { it.copy(scanning = false, scanned = true) }
        }
    }

    fun select(receiver: CastReceiver) {
        if (_state.value.connecting) return
        scanJob?.cancel()
        _state.update { it.copy(connecting = true, scanning = false, scanned = true, error = null) }
        viewModelScope.launch {
            val previous = controller
            val next = sources.firstOrNull { it.kind == receiver.kind }?.let { source -> runCatching { source.connect(receiver) }.getOrNull() }
            if (next == null) {
                _state.update { it.copy(connecting = false, error = CastError.CONNECT) }
                return@launch
            }
            if (previous != null && previous !== next) { previous.release() }
            controller = next
            _state.update { it.copy(connecting = false, selected = receiver, casting = false) }
            if (_state.value.waitingForReceiver) { _state.update { it.copy(waitingForReceiver = false) }; send() }
        }
    }

    /** Casts [items] in order; photos advance on their own while [CastUiState.slideshow] is on. */
    fun cast(items: List<CastItem>) {
        if (items.isEmpty()) return
        _state.update { it.copy(queue = items, index = 0, casting = false, error = null, status = CastStatus()) }
        if (controller == null) { _state.update { it.copy(waitingForReceiver = true) }; return }
        send()
    }

    /** Casts a pasted http(s) link; false when it is not one. */
    fun castLink(link: String): Boolean {
        val media = webMedia(link) ?: run { _state.update { it.copy(error = CastError.BAD_LINK) }; return false }
        cast(listOf(CastItem(media.title, media.kind, media.mime, url = media.url)))
        return true
    }

    fun next() = jump(_state.value.index + 1)
    fun previous() = jump(_state.value.index - 1)
    private fun jump(index: Int) {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        _state.update { it.copy(index = index, casting = false, status = CastStatus()) }
        send()
    }

    fun togglePlay() = command {
        val playing = _state.value.status.state == PlaybackState.PLAYING
        val ok = if (playing) it.pause() else it.play()
        if (ok) _state.update { s -> s.copy(status = s.status.copy(state = if (playing) PlaybackState.PAUSED else PlaybackState.PLAYING)) }
        ok
    }
    fun seekTo(positionMs: Long) = command {
        val target = positionMs.coerceIn(0, _state.value.status.durationMs.takeIf { d -> d > 0 } ?: Long.MAX_VALUE)
        it.seek(target).also { ok -> if (ok) _state.update { s -> s.copy(status = s.status.copy(positionMs = target)) } }
    }
    fun skip(deltaMs: Long) = seekTo(_state.value.status.positionMs + deltaMs)
    fun changeVolume(delta: Int) = command {
        val target = ((_state.value.status.volume ?: 50) + delta).coerceIn(0, 100)
        it.setVolume(target).also { ok -> if (ok) _state.update { s -> s.copy(status = s.status.copy(volume = target)) } }
    }
    fun toggleSlideshow() = _state.update { it.copy(slideshow = !it.slideshow) }

    /** Stops playback on the TV and stops serving files; the receiver stays chosen. */
    fun stop() {
        sendJob?.cancel(); pollJob?.cancel()
        val active = controller
        _state.update { it.copy(queue = emptyList(), index = 0, casting = false, sending = false, status = CastStatus()) }
        viewModelScope.launch { active?.stop(); stopSharing() }
    }

    /** Forgets the chosen receiver (after stopping what it plays). */
    fun disconnect() {
        stop()
        controller?.release(); controller = null
        _state.update { it.copy(selected = null) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun command(action: suspend (CastController) -> Boolean) {
        val active = controller ?: return
        viewModelScope.launch { if (!action(active)) _state.update { it.copy(error = CastError.CONTROL) } }
    }

    private fun send() {
        val active = controller ?: return
        val item = _state.value.current ?: return
        sendJob?.cancel(); pollJob?.cancel()
        _state.update { it.copy(sending = true, casting = false, error = null) }
        sendJob = viewModelScope.launch {
            val url = item.url ?: item.local?.let { media -> runCatching { share(media) }.getOrNull() }
            if (url == null) { _state.update { it.copy(sending = false, error = CastError.NO_WIFI) }; return@launch }
            val ok = active.load(CastMedia(url, item.title, item.mime, item.kind))
            _state.update { it.copy(sending = false, casting = ok, error = if (ok) null else CastError.LOAD) }
            if (!ok) return@launch
            events.log(AppEvent.FEATURE_USED, "feature" to "mirror", "media" to item.kind, "receiver" to active.receiver.kind)
            poll(active)
        }
    }

    private fun poll(active: CastController) {
        pollJob = viewModelScope.launch {
            var shownMs = 0L
            while (isActive) {
                active.status()?.let { status -> _state.update { it.copy(status = status) } }
                delay(pollMs)
                val s = _state.value
                if (s.current?.kind == MediaKind.PHOTO && s.slideshow && s.index < s.queue.lastIndex) {
                    shownMs += pollMs
                    if (shownMs >= slideMs) { jump(s.index + 1); return@launch }
                } else if (s.current?.kind != MediaKind.PHOTO && s.status.state == PlaybackState.STOPPED && s.index < s.queue.lastIndex && s.status.durationMs > 0) {
                    // The receiver finished this track or video: play the next one in the queue.
                    jump(s.index + 1); return@launch
                }
            }
        }
    }

    override fun onCleared() {
        controller?.release()
    }
}
