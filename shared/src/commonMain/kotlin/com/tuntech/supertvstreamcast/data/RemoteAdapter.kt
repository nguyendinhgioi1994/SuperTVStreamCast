package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.TlsOpener
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import com.tuntech.supertvstreamcast.platform.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel

/** A connection is reported only after the TV answers its protocol handshake. */
data class RemoteSession(val device: TvDevice, val capabilities: RemoteCapabilities)

interface RemoteAdapter {
    suspend fun connect(host: String, secret: String): RemoteSession
    suspend fun send(key: RemoteKey)
    suspend fun sendText(text: String)
    suspend fun apps(): List<TvApp>
    suspend fun launch(app: TvApp)
    suspend fun move(dx: Int, dy: Int): Unit = error("Pointer unsupported")
    suspend fun click(): Unit = error("Pointer unsupported")
    fun disconnect()
}

interface TextSocket {
    suspend fun send(text: String)
    /** Next text frame; throws when the socket is closed. */
    suspend fun receive(): String
    fun close()
}
/** Opens a socket to [url] on the TV at host; implementations scope TLS trust to that host. */
typealias SocketOpener = suspend (host: String, url: String) -> TextSocket

class KtorTextSocket(private val session: DefaultClientWebSocketSession) : TextSocket {
    override suspend fun send(text: String) = session.send(Frame.Text(text))
    override suspend fun receive(): String {
        while (true) { val frame = session.incoming.receive(); if (frame is Frame.Text) return frame.readText() }
    }
    override fun close() = session.cancel()
}

/** One client per TV host for the session, so pinned certificates do not outlive a disconnect. */
class LocalSockets(private val clientFor: (String) -> HttpClient) {
    private val clients = mutableMapOf<String, HttpClient>()
    val http: (String) -> HttpClient = { host -> clients.getOrPut(host) { clientFor(host) } }
    val open: SocketOpener = { host, url -> KtorTextSocket(http(host).webSocketSession(url)) }
    fun close() { clients.values.forEach { it.close() }; clients.clear() }
}

/** Plain (non-TLS) WebSocket opener for discovery probes. */
fun plainSockets(client: HttpClient): SocketOpener = { _, url -> KtorTextSocket(client.webSocketSession(url)) }

/** The TV answered but refuses remote control from phones (Roku: "Control by mobile apps" is off). */
class RemoteControlDisabled : Exception()
/** The TV identified itself but needs a pairing method this app does not implement (Panasonic encrypted models). */
class UnsupportedTvModel : Exception()

/**
 * Adapter for a known [platform], or for every platform the [brand] ships (detected on connect).
 * [tls] is only needed by platforms that speak raw TLS (Google TV, VIDAA); [localHttp] gives the
 * certificate-pinned client for TVs serving HTTPS with a self-signed certificate (Vizio, Fire TV).
 */
fun createAdapter(
    brand: TvBrand, http: HttpClient, sockets: SocketOpener, secrets: SecretStore = MemorySecretStore(),
    tls: TlsOpener? = null, platform: TvPlatform? = null, localHttp: (String) -> HttpClient = { http },
): RemoteAdapter? {
    val platforms = platform?.let { listOf(it) } ?: brand.platforms
    fun create(platform: TvPlatform): RemoteAdapter? = when (platform) {
        TvPlatform.SONY_BRAVIA -> SonyBraviaAdapter(http)
        TvPlatform.SAMSUNG_TIZEN -> SamsungTizenAdapter(http, secrets, sockets)
        TvPlatform.LG_WEBOS -> LgWebOsAdapter(secrets, sockets)
        TvPlatform.GOOGLE_TV -> tls?.let { GoogleTvAdapter(it, secrets) }
        TvPlatform.ROKU -> RokuEcpAdapter(http)
        TvPlatform.VIDAA -> tls?.let { VidaaAdapter(it, secrets) }
        TvPlatform.FIRE_TV -> FireTvAdapter(http, localHttp, secrets)
        TvPlatform.VIZIO -> VizioAdapter(localHttp, secrets)
        TvPlatform.PANASONIC_VIERA -> PanasonicVieraAdapter(http)
    }
    return if (platforms.size == 1) create(platforms[0]) else AutoDetectAdapter(platforms.map { { create(it) } })
}
