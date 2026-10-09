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
    val open: SocketOpener = { host, url -> KtorTextSocket(clients.getOrPut(host) { clientFor(host) }.webSocketSession(url)) }
    fun close() { clients.values.forEach { it.close() }; clients.clear() }
}

/** Plain (non-TLS) WebSocket opener for discovery probes. */
fun plainSockets(client: HttpClient): SocketOpener = { _, url -> KtorTextSocket(client.webSocketSession(url)) }

/** [tls] is only needed by brands that authenticate with a client certificate (Google TV). */
fun createAdapter(brand: TvBrand, http: HttpClient, sockets: SocketOpener, secrets: SecretStore = MemorySecretStore(), tls: TlsOpener? = null): RemoteAdapter? = when (brand) {
    TvBrand.SONY -> SonyBraviaAdapter(http)
    TvBrand.SAMSUNG -> SamsungTizenAdapter(http, secrets, sockets)
    TvBrand.LG -> LgWebOsAdapter(secrets, sockets)
    TvBrand.GOOGLE -> tls?.let { GoogleTvAdapter(it, secrets) }
    else -> null
}
