package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Panasonic Viera network remote (SOAP on port 55000). Connected once the TV described itself as a Viera
 * remote and answered a volume query. Models that require an encrypted PIN session are reported with
 * [UnsupportedTvModel]. Each key press gets an HTTP status, so a refused command is detected.
 */
class PanasonicVieraAdapter(private val client: HttpClient) : RemoteAdapter {
    private var ip = ""

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        disconnect()
        val device = checkNotNull(PanasonicProtocol.identity(host, text(client.get(PanasonicProtocol.description(host)))))
        if (PanasonicProtocol.needsEncryption(text(client.get(PanasonicProtocol.actions(host))))) throw UnsupportedTvModel()
        checkNotNull(PanasonicProtocol.volume(soap(PanasonicProtocol.rendering(host), PanasonicProtocol.getVolume())))
        ip = host
        return RemoteSession(device, PanasonicProtocol.capabilities)
    }
    override suspend fun send(key: RemoteKey) { soap(PanasonicProtocol.control(active()), PanasonicProtocol.sendKey(key)) }
    override suspend fun sendText(text: String): Unit = error("Text unsupported")
    override suspend fun apps(): List<TvApp> = error("Apps unsupported")
    override suspend fun launch(app: TvApp): Unit = error("Apps unsupported")
    private suspend fun soap(url: String, request: PanasonicProtocol.Soap): String = text(client.post(url) {
        header("SOAPACTION", request.soapAction)
        contentType(ContentType.Text.Xml.withParameter("charset", "utf-8"))
        setBody(request.body)
    })
    private fun active() = ip.also { check(it.isNotEmpty()) }
    private suspend fun text(response: HttpResponse): String {
        check(response.status.isSuccess())
        return response.bodyAsText()
    }
    override fun disconnect() { ip = "" }
}
