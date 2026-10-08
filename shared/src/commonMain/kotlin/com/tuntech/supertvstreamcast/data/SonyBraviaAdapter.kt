package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/** Sony BRAVIA IP control: PSK header, IRCC codes discovered from the TV, Scalar API for apps/text. */
class SonyBraviaAdapter(private val client: HttpClient) : RemoteAdapter {
    private var ip = ""
    private var psk = ""
    private var codes = emptyMap<String, String>()

    override suspend fun connect(host: String, secret: String): RemoteSession {
        require(isLocalIpv4(host))
        disconnect()
        val discovered = SonyProtocol.codes(api(host, secret, "system", SonyProtocol.request("getRemoteControllerInfo")))
        check(discovered.containsKey("Confirm"))
        val device = runCatching { SonyProtocol.identity(host, api(host, "", "system", SonyProtocol.request("getInterfaceInformation"))) }.getOrNull()
        ip = host; psk = secret; codes = discovered
        return RemoteSession(device ?: TvDevice(TvBrand.SONY, host, TvBrand.SONY.title), SonyProtocol.capabilities(discovered))
    }
    override suspend fun send(key: RemoteKey) {
        val code = SonyProtocol.code(codes, key) ?: error("Unsupported key")
        val response = client.post("http://$ip/sony/ircc") {
            header("X-Auth-PSK", psk)
            header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
            contentType(ContentType.Text.Xml)
            setBody(SonyProtocol.ircc(code))
        }
        check(response.status.isSuccess())
    }
    override suspend fun sendText(text: String) {
        SonyProtocol.result(api(ip, psk, "appControl", SonyProtocol.request("setTextForm", buildJsonArray { add(text) }, 601)))
    }
    override suspend fun apps(): List<TvApp> = SonyProtocol.apps(api(ip, psk, "appControl", SonyProtocol.request("getApplicationList", id = 60)))
    override suspend fun launch(app: TvApp) {
        SonyProtocol.result(api(ip, psk, "appControl", SonyProtocol.request("setActiveApp", buildJsonArray { addJsonObject { put("uri", app.id) } }, 601)))
    }
    private suspend fun api(host: String, key: String, service: String, body: String): String {
        check(host.isNotEmpty())
        val response = client.post("http://$host/sony/$service") {
            if (key.isNotEmpty()) header("X-Auth-PSK", key)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        check(response.status.isSuccess())
        return response.bodyAsText()
    }
    override fun disconnect() { ip = ""; psk = ""; codes = emptyMap() }
}
