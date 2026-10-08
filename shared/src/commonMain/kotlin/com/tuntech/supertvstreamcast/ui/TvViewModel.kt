package com.tuntech.supertvstreamcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuntech.supertvstreamcast.data.*
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.*
import com.tuntech.supertvstreamcast.platform.AppPreferences
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class UiError { CONNECTION, COMMAND, PLAYLIST, INVALID_IP, PLAYER, MIRROR, PERMISSION, NO_WIFI, APPS }
data class TvUiState(
    val step: Int = 0, val brand: TvBrand = TvBrand.SAMSUNG, val goal: Feature = Feature.REMOTE,
    val tab: Feature = Feature.HOME, val connectionOpen: Boolean = false, val connected: Boolean = false,
    val busy: Boolean = false, val error: UiError? = null, val channels: List<Channel> = emptyList(),
    val favorites: Set<String> = emptySet(), val player: Channel? = null,
    val device: TvDevice? = null, val capabilities: RemoteCapabilities = RemoteCapabilities(),
    val apps: List<TvApp> = emptyList(), val appsLoading: Boolean = false,
    val scanning: Boolean = false, val scanned: Boolean = false, val discovered: List<TvDevice> = emptyList(),
    val lastHost: String = "", val recent: List<String> = emptyList(),
) {
    fun can(key: RemoteKey) = connected && key in capabilities.keys
}
class TvViewModel(
    private val prefs: AppPreferences,
    private val repository: TvRepository = TvRepository(),
    private val http: HttpClient = HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 5_000 }
    },
    private val sockets: LocalSockets = LocalSockets(::pinnedLocalClient),
    private val discoveryHttp: HttpClient = discoveryClient(),
    private val localIp: () -> String? = ::localIpv4Address,
) : ViewModel() {
    private val savedDevice = prefs.lastDevice.split('|').takeIf { it.size == 3 && isLocalIpv4(it[1]) }
    private val mutable = MutableStateFlow(TvUiState(
        step = if (prefs.onboardingDone) 3 else 0,
        brand = TvBrand.entries.firstOrNull { it.name == prefs.brand } ?: TvBrand.SAMSUNG,
        goal = Feature.entries.firstOrNull { it.name == prefs.goal } ?: Feature.REMOTE,
        lastHost = savedDevice?.get(1).orEmpty(),
    ))
    val state: StateFlow<TvUiState> = mutable.asStateFlow()
    private val discovery = TvDiscovery(discoveryHttp, plainSockets(discoveryHttp))
    private var adapter: RemoteAdapter? = null
    private val commands = Mutex()

    fun next() {
        mutable.update { it.copy(step = (it.step + 1).coerceAtMost(3)) }
        if (state.value.step == 3) {
            prefs.brand = state.value.brand.name; prefs.goal = state.value.goal.name; prefs.onboardingDone = true
            mutable.update { it.copy(tab = it.goal) }
        }
    }
    fun back() = mutable.update {
        when {
            it.connectionOpen -> if(it.busy) it else it.copy(connectionOpen=false,error=null)
            it.player!=null -> it.copy(player=null,error=null)
            it.step<3 -> it.copy(step=(it.step-1).coerceAtLeast(0))
            else -> it.copy(tab=Feature.HOME,error=null)
        }
    }
    fun brand(brand: TvBrand) {
        if (state.value.busy) return
        if (brand != state.value.brand) disconnect()
        prefs.brand = brand.name
        mutable.update { it.copy(brand = brand, error = null) }
    }
    fun goal(goal: Feature) = mutable.update { it.copy(goal = goal) }
    fun tab(tab: Feature) = mutable.update { it.copy(tab = tab, error = null, player = null) }
    fun connection(open: Boolean) { if (!state.value.busy) mutable.update { it.copy(connectionOpen = open, error = null) } }
    fun disconnect() {
        adapter?.disconnect(); adapter = null; sockets.close()
        mutable.update { it.copy(connected = false, capabilities = RemoteCapabilities(), apps = emptyList()) }
    }
    fun clearError() = mutable.update { it.copy(error = null) }
    fun report(error: UiError) = mutable.update { it.copy(error = error) }

    /** Picking a discovered TV switches brand; PSK brands still need the key typed by the user. */
    fun pick(device: TvDevice) {
        brand(device.brand)
        mutable.update { it.copy(lastHost = device.host) }
        if (!device.brand.needsPsk) connect(device.host, "")
    }
    fun connect(ip: String, secret: String) {
        val host = ip.trim()
        if (!isLocalIpv4(host)) { report(UiError.INVALID_IP); return }
        val brand = state.value.brand
        if (!brand.hasRemoteAdapter) return
        disconnect()
        val created = createAdapter(brand, http, sockets.open) ?: return
        work(UiError.CONNECTION) {
            val session = created.connect(host, secret)
            adapter = created
            prefs.lastDevice = listOf(session.device.brand.name, host, session.device.name.replace('|', ' ')).joinToString("|")
            mutable.update { it.copy(connected = true, connectionOpen = false, device = session.device, capabilities = session.capabilities, lastHost = host) }
            if (session.capabilities.apps) loadApps()
        }
    }
    fun scan() {
        if (state.value.scanning) return
        val ip = localIp()
        if (ip == null) { report(UiError.NO_WIFI); return }
        mutable.update { it.copy(scanning = true, discovered = emptyList(), error = null) }
        viewModelScope.launch {
            try { discovery.scan(ip) { device -> mutable.update { it.copy(discovered = (it.discovered + device).sortedBy { d -> d.host.substringAfterLast('.').toInt() }) } } }
            finally { mutable.update { it.copy(scanning = false, scanned = true) } }
        }
    }
    fun send(key: RemoteKey) { if (state.value.can(key)) command { it.send(key) } }
    fun sendText(text: String) { if (text.isNotEmpty() && state.value.capabilities.text) command { it.sendText(text) } }
    fun launch(app: TvApp) = command { it.launch(app) }
    fun move(dx: Int, dy: Int) { if (state.value.capabilities.pointer) command { it.move(dx, dy) } }
    fun click() { if (state.value.capabilities.pointer) command { it.click() } }
    fun loadApps() {
        val current = adapter ?: return
        if (state.value.appsLoading) return
        mutable.update { it.copy(appsLoading = true) }
        viewModelScope.launch {
            try {
                val apps = commands.withLock { current.apps() }
                mutable.update { it.copy(apps = apps.sortedBy { app -> app.title.lowercase() }) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                mutable.update { it.copy(error = UiError.APPS) }
            } finally { mutable.update { it.copy(appsLoading = false) } }
        }
    }
    fun importPlaylist(value: String) = work(UiError.PLAYLIST) {
        val channels = ParsePlaylistUseCase()(repository.loadPlaylist(value))
        val urls = channels.map { c -> c.url }.toSet()
        mutable.update { it.copy(channels = channels, favorites = it.favorites.intersect(urls), recent = it.recent.filter { u -> u in urls }, player = null) }
    }
    fun play(channel: Channel) = mutable.update { it.copy(player = channel, error = null, recent = withRecent(it.recent, channel.url)) }
    fun zap(step: Int) { val current = state.value.player ?: return; adjacentChannel(state.value.channels, current, step)?.let(::play) }
    fun favorite(url: String) = mutable.update { it.copy(favorites = if (url in it.favorites) it.favorites - url else it.favorites + url) }

    /** Remote commands run in order without blocking the UI; a failure ends the session honestly. */
    private fun command(action: suspend (RemoteAdapter) -> Unit) {
        val current = adapter ?: return
        if (!state.value.connected) return
        viewModelScope.launch {
            commands.withLock {
                if (adapter !== current) return@withLock
                try { action(current) } catch (e: CancellationException) { throw e } catch (_: Exception) {
                    disconnect(); mutable.update { it.copy(error = UiError.COMMAND) }
                }
            }
        }
    }
    private fun work(error: UiError, action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (error == UiError.CONNECTION) disconnect()
                mutable.update { it.copy(error = error) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() { disconnect(); repository.close(); http.close(); discoveryHttp.close() }
}
