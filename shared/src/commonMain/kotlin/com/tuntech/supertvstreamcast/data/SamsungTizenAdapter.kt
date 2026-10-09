package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import com.tuntech.supertvstreamcast.platform.SecretStore
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeout

/** Samsung Tizen remote channel. The pairing token the TV issues is kept in [secrets] per TV address. */
class SamsungTizenAdapter(private val client: HttpClient, private val secrets: SecretStore = MemorySecretStore(), private val open: SocketOpener) : RemoteAdapter {
    private var socket: TextSocket? = null

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        disconnect()
        val response = client.get("http://$host:8001/api/v2/")
        check(response.status.isSuccess())
        val info = checkNotNull(SamsungProtocol.info(host, response.bodyAsText()))
        val opened = open(host, SamsungProtocol.url(host, info.tokenAuth, secrets.get("samsung.token.$host")))
        try {
            // The viewer has to accept the request on the TV, so allow time for that.
            withTimeout(45_000) {
                while (true) when (val event = SamsungProtocol.event(opened.receive())) {
                    is SamsungProtocol.Event.Connected -> { event.token?.let { secrets.put("samsung.token.$host", it) }; break }
                    SamsungProtocol.Event.Denied -> error("Pairing denied")
                    else -> Unit
                }
            }
        } catch (e: Throwable) { opened.close(); throw e }
        socket = opened
        return RemoteSession(info.device, SamsungProtocol.capabilities)
    }
    override suspend fun send(key: RemoteKey) = active().send(SamsungProtocol.key(key))
    override suspend fun sendText(text: String) = active().send(SamsungProtocol.text(text))
    private val appTypes = mutableMapOf<String, Int>()
    override suspend fun apps(): List<TvApp> {
        val socket = active()
        socket.send(SamsungProtocol.installedApps())
        return withTimeout(6_000) {
            var result: List<TvApp>? = null
            while (result == null) {
                val event = SamsungProtocol.event(socket.receive())
                if (event is SamsungProtocol.Event.Apps) {
                    event.apps.forEach { (app, type) -> appTypes[app.id] = type }
                    result = event.apps.map { it.first }
                }
            }
            result
        }
    }
    override suspend fun launch(app: TvApp) = active().send(SamsungProtocol.launch(app, appTypes[app.id] ?: 2))
    private fun active() = checkNotNull(socket)
    override fun disconnect() { socket?.close(); socket = null }
}
