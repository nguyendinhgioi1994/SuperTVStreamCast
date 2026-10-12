package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Roku ECP (Roku players and Roku TVs from TCL, Hisense and others). Connected once the device answered
 * `query/device-info` and `query/apps`, and allowed an icon query, which Roku OS 14.1+ refuses (403)
 * while "Control by mobile apps" is off. Key presses get an HTTP status, so failures are detected.
 */
class RokuEcpAdapter(private val client: HttpClient) : RemoteAdapter {
    private var ip = ""

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        disconnect()
        val info = checkNotNull(RokuProtocol.info(host, text(client.get("${RokuProtocol.base(host)}/query/device-info"))))
        val apps = RokuProtocol.apps(text(client.get("${RokuProtocol.base(host)}/query/apps")))
        apps.firstOrNull()?.let { checked(client.get(RokuProtocol.icon(host, it))) }
        ip = host
        return RemoteSession(info.device, RokuProtocol.capabilities(info))
    }
    override suspend fun send(key: RemoteKey) = press(RokuProtocol.keyName(key) ?: error("Unsupported key"))
    override suspend fun sendText(text: String) = RokuProtocol.literals(text).forEach { press(it) }
    override suspend fun apps(): List<TvApp> = RokuProtocol.apps(text(client.get("${RokuProtocol.base(active())}/query/apps")))
    override suspend fun launch(app: TvApp) { checked(client.post(RokuProtocol.launch(active(), app))) }
    private suspend fun press(name: String) { checked(client.post(RokuProtocol.keypress(active(), name))) }
    private fun active() = ip.also { check(it.isNotEmpty()) }
    private suspend fun text(response: HttpResponse) = checked(response).bodyAsText()
    private fun checked(response: HttpResponse): HttpResponse {
        if (response.status == HttpStatusCode.Forbidden) throw RemoteControlDisabled()
        check(response.status.isSuccess())
        return response
    }
    override fun disconnect() { ip = "" }
}
