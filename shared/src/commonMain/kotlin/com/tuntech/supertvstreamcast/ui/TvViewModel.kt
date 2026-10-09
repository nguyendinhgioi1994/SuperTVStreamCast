package com.tuntech.supertvstreamcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuntech.supertvstreamcast.data.*
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.*
import com.tuntech.supertvstreamcast.platform.AppPreferences
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import com.tuntech.supertvstreamcast.platform.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class UiError { CONNECTION, PAIRING_CODE, WAKE, COMMAND, PLAYLIST, INVALID_IP, PLAYER, MIRROR, PERMISSION, NO_WIFI, APPS,
    XTREAM_INPUT, XTREAM, XTREAM_AUTH, FILE, STREAM_URL, GUIDE, GUIDE_NO_CHANNELS, DOWNLOAD }
data class TvUiState(
    val brand: TvBrand = TvBrand.SAMSUNG,
    val tab: Feature = Feature.HOME, val connectionOpen: Boolean = false, val connected: Boolean = false,
    val busy: Boolean = false, val error: UiError? = null,
    val device: TvDevice? = null, val capabilities: RemoteCapabilities = RemoteCapabilities(),
    val apps: List<TvApp> = emptyList(), val appsLoading: Boolean = false,
    val scanning: Boolean = false, val scanned: Boolean = false, val discovered: List<TvDevice> = emptyList(),
    val lastHost: String = "",
    /** The TV is showing a pairing code that has to be typed in. */
    val codeRequested: Boolean = false,
    /** Hosts whose network adapter address is known, so a Wake-on-LAN packet can be sent to them. */
    val wakeHosts: Set<String> = emptySet(),
    /** A wake packet went out; nothing confirms that the TV received it. */
    val wakeSent: Boolean = false,
) {
    fun can(key: RemoteKey) = connected && key in capabilities.keys
}
/** TV choice, tabs and the remote control. The IPTV library has its own [com.tuntech.supertvstreamcast.ui.iptv.IptvViewModel]. */
class TvViewModel(
    private val prefs: AppPreferences,
    private val http: HttpClient = HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 5_000 }
    },
    private val sockets: LocalSockets = LocalSockets(::pinnedLocalClient),
    private val discoveryHttp: HttpClient = discoveryClient(),
    private val localIp: () -> String? = ::localIpv4Address,
    /** Pairing tokens, pinned TV certificates and Wake-on-LAN addresses. */
    private val secrets: SecretStore = MemorySecretStore(),
    private val tls: TlsOpener = ::openTvTls,
    private val broadcast: suspend (String, Int, ByteArray) -> Unit = ::sendBroadcast,
    /** Tab opened first; onboarding passes the goal the user picked. */
    startTab: Feature = Feature.HOME,
) : ViewModel() {
    private val savedDevice = prefs.lastDevice.split('|').takeIf { it.size == 3 && isLocalIpv4(it[1]) }
    private val mutable = MutableStateFlow(TvUiState(
        brand = TvBrand.entries.firstOrNull { it.name == prefs.brand } ?: TvBrand.SAMSUNG,
        tab = startTab,
        lastHost = savedDevice?.get(1).orEmpty(),
        wakeHosts = setOfNotNull(savedDevice?.get(1)?.takeIf { secrets.get(macKey(it)) != null }),
    ))
    val state: StateFlow<TvUiState> = mutable.asStateFlow()
    private val discovery = TvDiscovery(discoveryHttp, plainSockets(discoveryHttp))
    private var adapter: RemoteAdapter? = null
    /** Adapter holding an open pairing session while the user reads the code off the TV. */
    private var pairing: RemoteAdapter? = null
    private val commands = Mutex()

    fun back() = mutable.update {
        when {
            it.connectionOpen -> if(it.busy) it else it.copy(connectionOpen=false,error=null)
            else -> it.copy(tab=Feature.HOME,error=null)
        }
    }
    fun brand(brand: TvBrand) {
        if (state.value.busy) return
        if (brand != state.value.brand) { disconnect(); dropPairing() }
        prefs.brand = brand.name
        mutable.update { it.copy(brand = brand, error = null) }
    }
    fun tab(tab: Feature) = mutable.update { it.copy(tab = tab, error = null) }
    fun connection(open: Boolean) {
        if (state.value.busy) return
        if (!open) dropPairing()
        mutable.update { it.copy(connectionOpen = open, error = null, wakeSent = false) }
    }
    private fun dropPairing() {
        pairing?.disconnect(); pairing = null
        mutable.update { it.copy(codeRequested = false) }
    }
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
        // A typed code continues the pairing session that made the TV show it.
        val resumed = pairing?.takeIf { state.value.codeRequested && secret.isNotBlank() }
        if (resumed == null) dropPairing()
        val created = resumed ?: createAdapter(brand, http, sockets.open, secrets, tls) ?: return
        mutable.update { it.copy(wakeSent = false) }
        work(UiError.CONNECTION, { e -> if (e is PairingCodeInvalid) UiError.PAIRING_CODE else null }) {
            val session = try { created.connect(host, secret.trim()) } catch (e: Exception) {
                when (e) {
                    is PairingCodeRequired -> { pairing = created; mutable.update { it.copy(codeRequested = true, lastHost = host) }; return@work }
                    is PairingCodeInvalid, is CancellationException -> Unit
                    else -> { created.disconnect(); if (pairing === created) dropPairing() }
                }
                throw e
            }
            pairing = null
            adapter = created
            if (macBytes(session.device.mac) != null) secrets.put(macKey(host), session.device.mac)
            prefs.lastDevice = listOf(session.device.brand.name, host, session.device.name.replace('|', ' ')).joinToString("|")
            mutable.update { it.copy(connected = true, connectionOpen = false, codeRequested = false, device = session.device, capabilities = session.capabilities, lastHost = host,
                wakeHosts = if (secrets.get(macKey(host)) != null) it.wakeHosts + host else it.wakeHosts) }
            if (session.capabilities.apps) loadApps()
        }
    }
    /** Sends a Wake-on-LAN packet for a TV that reported its adapter address earlier. Delivery cannot be confirmed. */
    fun wake(ip: String) {
        val host = ip.trim()
        val packet = secrets.get(macKey(host))?.let(::magicPacket)
        val target = subnetBroadcast(host)
        if (packet == null || target == null) { report(UiError.WAKE); return }
        mutable.update { it.copy(wakeSent = false) }
        work(UiError.WAKE) {
            for (address in listOf(target, "255.255.255.255")) repeat(3) { broadcast(address, 9, packet) }
            mutable.update { it.copy(wakeSent = true) }
        }
    }
    /** Forgets every stored pairing token, pinned certificate, wake address and the last TV. */
    fun forgetTvs() {
        if (state.value.busy) return
        disconnect(); dropPairing()
        secrets.clear()
        prefs.lastDevice = ""
        mutable.update { it.copy(lastHost = "", wakeHosts = emptySet(), wakeSent = false, device = null, error = null) }
    }
    private fun macKey(host: String) = "mac.$host"
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
    private fun work(error: UiError, classify: (Exception) -> UiError? = { null }, action: suspend () -> Unit): Job? {
        if (state.value.busy) return null
        mutable.update { it.copy(busy = true, error = null) }
        return viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (error == UiError.CONNECTION) disconnect()
                mutable.update { it.copy(error = classify(e) ?: error) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() { disconnect(); pairing?.disconnect(); http.close(); discoveryHttp.close() }
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun epochSeconds(): Long = kotlin.time.Clock.System.now().epochSeconds
